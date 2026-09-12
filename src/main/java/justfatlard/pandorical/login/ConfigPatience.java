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
 * Time for a slow machine to take the content in.
 *
 * <p>Taking in the content sync means a resource reload, and a client does not answer the server
 * while it reloads. Vanilla gives a silent connection thirty seconds before the read timeout ends
 * it, and fifteen before a missed keep-alive does. A fast machine is done in seven. A laptop with
 * little memory to spare took longer than thirty every time, was timed out in the middle of the
 * reload, and crashed natively a moment later as it tore down a reload still running: twice for
 * one player in one evening, and the same crash, at the same point, for another before him.
 *
 * <p>So while a connection's sync is out and unanswered it gets five minutes rather than thirty
 * seconds, the keep-alive clock is held rather than run down, and a ping goes out every ten
 * seconds so the client's own read timeout, which is also thirty seconds, does not end it from
 * the other side. The ack puts everything back. The keep-alive hold and the ping are
 * ConfigPatienceMixin's; this class owns the clock and the read timeout. Five minutes is a ceiling, not a target: a
 * client that has not answered by then is not coming back, and {@link #expire} closes it.
 */
public final class ConfigPatience {
	private ConfigPatience() {}

	private static final int VANILLA_SECONDS = 30;
	private static final int PATIENT_SECONDS = 300;
	private static final long LONGEST_MILLIS = PATIENT_SECONDS * 1000L;
	private static final String TIMEOUT_HANDLER = "timeout";

	/**
	 * Patient connections one address may hold at once. Patience is given before anything is known
	 * about a connection, so without a limit a handful of silent ones would each keep the whole
	 * sync queued for five minutes. A household behind one address still fits.
	 */
	private static final int MOST_PER_ADDRESS = 8;

	private record Wait(long since, InetAddress from) {}

	/** Connections whose sync is out, and when it went. Weak, so a dropped one is not kept alive here. */
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

	/** Close every connection whose sync has gone unanswered past the ceiling. Server thread, once a tick. */
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
