package justfatlard.pandorical.login;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.api.KeepsakeApi;
import justfatlard.pandorical.protocol.KeepsakeStoreS2C;
import justfatlard.pandorical.protocol.KeepsakesAskConfigS2C;
import justfatlard.pandorical.protocol.KeepsakesConfigC2S;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.util.Util;

/** The server's half of {@link KeepsakeApi}: its own id, the login ask, and what came back. */
public final class Keepsakes implements KeepsakeApi {
	public static final Keepsakes INSTANCE = new Keepsakes();

	private Keepsakes() {}

	/** What each player's game handed back at login, by profile id. */
	private final Map<UUID, Map<String, String>> received = new ConcurrentHashMap<>();

	private String serverId;

	/**
	 * This server's own name for itself, made once and kept: the address players type can change,
	 * and two servers on one address are still two servers.
	 */
	public synchronized String serverId() {
		if (serverId != null) return serverId;
		Path file = FabricLoader.getInstance().getConfigDir().resolve("pandorical").resolve("server-id");
		try {
			if (Files.exists(file)) {
				String read = Files.readString(file).trim();
				if (read.matches("[0-9a-f-]{36}")) return serverId = read;
			}
			serverId = UUID.randomUUID().toString();
			Files.createDirectories(file.getParent());
			Files.writeString(file, serverId + "\n");
		} catch (IOException e) {
			Pandorical.LOGGER.warn("[pandorical] could not keep the server id at {}: {}", file, e.getMessage());
			if (serverId == null) serverId = UUID.randomUUID().toString();
		}
		return serverId;
	}

	/** A login starting under this name: nothing an earlier login's game handed back carries into this one. */
	public void begin(ServerConfigurationPacketListenerImpl handler) {
		var profile = handler.getOwner();
		if (profile != null) received.remove(profile.id());
	}

	/** Whether this connection's game will answer the ask. */
	public boolean askable(ServerConfigurationPacketListenerImpl handler) {
		return ServerConfigurationNetworking.canSend(handler, KeepsakesAskConfigS2C.TYPE);
	}

	/** The answer arrived: kept for the join, and the login let on. */
	public void answered(ServerConfigurationPacketListenerImpl handler, KeepsakesConfigC2S payload) {
		var profile = handler.getOwner();
		if (profile == null) return;
		Map<String, String> kept = new HashMap<>();
		for (Map.Entry<String, String> entry : payload.values().entrySet()) {
			if (kept.size() >= MOST_KEYS) break;
			if (entry.getKey().length() > LONGEST_KEY || entry.getValue().length() > LONGEST_VALUE) continue;
			kept.put(entry.getKey(), entry.getValue());
		}
		received.put(profile.id(), kept);
		try {
			handler.completeTask(Task.TYPE);
		} catch (IllegalStateException e) {
			// Answered after the wait ran out: kept all the same, and the login already went on.
		}
	}

	public void forget(UUID playerId) {
		received.remove(playerId);
	}

	@Override
	public String get(ServerPlayer player, String key) {
		Map<String, String> values = received.get(player.getUUID());
		return values == null ? null : values.get(key);
	}

	@Override
	public boolean canKeep(ServerPlayer player) {
		return ServerPlayNetworking.canSend(player, KeepsakeStoreS2C.TYPE);
	}

	@Override
	public void put(ServerPlayer player, String key, String value) {
		if (key.length() > LONGEST_KEY || value.length() > LONGEST_VALUE) {
			throw new IllegalArgumentException("keepsake '" + key + "' is over the limit: keys " + LONGEST_KEY
				+ " characters, values " + LONGEST_VALUE);
		}
		if (value.isEmpty()) {
			remove(player, key);
			return;
		}
		if (!canKeep(player)) return;
		received.computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>()).put(key, value);
		ServerPlayNetworking.send(player, new KeepsakeStoreS2C(serverId(), key, value));
	}

	@Override
	public void remove(ServerPlayer player, String key) {
		Map<String, String> values = received.get(player.getUUID());
		if (values != null) values.remove(key);
		if (ServerPlayNetworking.canSend(player, KeepsakeStoreS2C.TYPE)) {
			ServerPlayNetworking.send(player, new KeepsakeStoreS2C(serverId(), key, ""));
		}
	}

	/**
	 * The ask, held open until the game answers so the answer is in hand before the player joins.
	 * Not for ever: a game that says nothing for ten seconds is let on without, since what is
	 * kept is a convenience and a login that hangs on it is not.
	 */
	public static final class Task implements ConfigurationTask {
		public static final Type TYPE = new Type("pandorical:keepsakes");
		private static final long WAIT_MILLIS = 10_000L;

		private long startedAt;

		@Override
		public void start(Consumer<Packet<?>> sender) {
			startedAt = Util.getMillis();
			sender.accept(ServerConfigurationNetworking.createClientboundPacket(
				new KeepsakesAskConfigS2C(INSTANCE.serverId())));
		}

		@Override
		public boolean tick() {
			return Util.getMillis() - startedAt > WAIT_MILLIS;
		}

		@Override
		public Type type() {
			return TYPE;
		}
	}
}
