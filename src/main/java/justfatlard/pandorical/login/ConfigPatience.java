package justfatlard.pandorical.login;

import io.netty.channel.Channel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.mixin.ConnectionChannelAccessor;
import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.util.Util;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * Time for a slow client to take in the content sync. Taking it in means a resource reload, during
 * which the client does not answer, and vanilla's read timeout or keep-alive would end the
 * connection. While a sync is unanswered this class raises the read timeout and
 * {@code ConfigPatienceMixin} holds the keep-alive; the ack restores both, and {@link #expire}
 * closes a connection that outlasts the ceiling.
 */
public final class ConfigPatience {
	private ConfigPatience() {}

	private static final int VANILLA_SECONDS = 30;
	private static final int PATIENT_SECONDS = 300;
	private static final long LONGEST_MILLIS = PATIENT_SECONDS * 1000L;
	private static final String TIMEOUT_HANDLER = "timeout";

	/** Patience is granted before a connection proves anything, so one address gets only this many. */
	private static final int MOST_PER_ADDRESS = 8;

	private record Wait(long since, InetAddress from) {}

	private static final Map<ServerConfigurationPacketListenerImpl, Wait> waiting =
		Collections.synchronizedMap(new WeakHashMap<>());

	public static void begin(ServerConfigurationPacketListenerImpl handler, Connection connection) {
		InetAddress from = connection.getRemoteAddress() instanceof InetSocketAddress socket ? socket.getAddress() : null;
		synchronized (waiting) {
			if (from != null && waiting.values().stream().filter(wait -> from.equals(wait.from())).count() >= MOST_PER_ADDRESS) {
				Pandorical.LOGGER.warn("[pandorical] {} already has {} connections waiting on the content sync; this one gets vanilla's timeout",
					from, MOST_PER_ADDRESS);
				return;
			}
			waiting.put(handler, new Wait(Util.getMillis(), from));
		}
		readTimeout(connection, PATIENT_SECONDS);
	}

	public static void end(ServerConfigurationPacketListenerImpl handler, Connection connection) {
		if (waiting.remove(handler) != null) readTimeout(connection, VANILLA_SECONDS);
	}

	public static void forget(ServerConfigurationPacketListenerImpl handler) {
		waiting.remove(handler);
	}

	/** Server thread, once a tick. */
	public static void expire() {
		if (waiting.isEmpty()) return;
		long now = Util.getMillis();
		List<ServerConfigurationPacketListenerImpl> expired = new ArrayList<>();
		synchronized (waiting) {
			waiting.forEach((handler, wait) -> {
				if (now - wait.since() >= LONGEST_MILLIS) expired.add(handler);
			});
			expired.forEach(waiting::remove);
		}
		for (ServerConfigurationPacketListenerImpl handler : expired) {
			handler.disconnect(Component.literal(
				"Pandorical: the content sync went unanswered for " + PATIENT_SECONDS / 60 + " minutes"));
		}
	}

	public static boolean isWaiting(ServerConfigurationPacketListenerImpl handler) {
		Wait wait = waiting.get(handler);
		return wait != null && Util.getMillis() - wait.since() < LONGEST_MILLIS;
	}

	private static void readTimeout(Connection connection, int seconds) {
		Channel channel = ((ConnectionChannelAccessor) connection).pandorical$channel();
		if (channel == null) return;
		channel.eventLoop().execute(() -> {
			if (channel.pipeline().get(TIMEOUT_HANDLER) == null) return;
			try {
				channel.pipeline().replace(TIMEOUT_HANDLER, TIMEOUT_HANDLER, new ReadTimeoutHandler(seconds));
			} catch (RuntimeException e) {
				Pandorical.LOGGER.warn("[pandorical] could not set the read timeout to {}s", seconds, e);
			}
		});
	}
}
