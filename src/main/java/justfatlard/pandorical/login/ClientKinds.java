package justfatlard.pandorical.login;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.api.ClientKind;
import justfatlard.pandorical.protocol.SyncContentConfigS2C;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Each connection's {@link ClientKind}, decided before the player exists.
 *
 * <p>Decided at {@code BEFORE_CONFIGURE} and kept by profile id, because the {@code ServerPlayer} and
 * its inventory menu are built at the end of configuration and have to know already. Kept until the
 * player leaves play, so a respawn, which builds a new {@code ServerPlayer}, reads the same answer.
 */
public final class ClientKinds {
	private ClientKinds() {}

	private static final Map<UUID, ClientKind> KINDS = new ConcurrentHashMap<>();
	/**
	 * Configured and not yet playing, with when. A connection turned away in configuration never
	 * reaches play's disconnect, so these are let go after a while instead; not on configuration's
	 * own disconnect, which also fires on the way into play.
	 */
	private static final Map<UUID, Long> CONFIGURING = new ConcurrentHashMap<>();
	private static final long CONFIGURING_MILLIS = 10 * 60 * 1000L;
	private static final List<Predicate<UUID>> BEDROCK_DETECTORS = new CopyOnWriteArrayList<>();

	public static void register() {
		ServerConfigurationConnectionEvents.BEFORE_CONFIGURE.register((handler, server) -> {
			var profile = handler.getOwner();
			if (profile == null) return;
			ClientKind kind = ServerConfigurationNetworking.canSend(handler, SyncContentConfigS2C.TYPE)
				? ClientKind.PANDORICAL
				: isBedrock(profile.id()) ? ClientKind.BEDROCK : ClientKind.VANILLA_JAVA;
			long now = System.currentTimeMillis();
			CONFIGURING.entrySet().removeIf(stale -> {
				if (now - stale.getValue() < CONFIGURING_MILLIS) return false;
				KINDS.remove(stale.getKey());
				return true;
			});
			KINDS.put(profile.id(), kind);
			CONFIGURING.put(profile.id(), now);
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			CONFIGURING.remove(handler.getPlayer().getUUID()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
			KINDS.remove(handler.getPlayer().getUUID()));
	}

	/**
	 * Asks the detectors again at joining. Geyser without Floodgate learns the player's id from the
	 * login reply it may still be reading while configuration starts. Both answers build the same
	 * inventory menu, so only the name can change here.
	 */
	public static void joined(UUID player) {
		if (KINDS.get(player) == ClientKind.VANILLA_JAVA && isBedrock(player)) KINDS.put(player, ClientKind.BEDROCK);
	}

	public static void addBedrockDetector(Predicate<UUID> detector) {
		BEDROCK_DETECTORS.add(detector);
	}

	/** {@link ClientKind#VANILLA_JAVA} for a player that never configured, such as a fake player. */
	public static ClientKind of(UUID player) {
		return KINDS.getOrDefault(player, ClientKind.VANILLA_JAVA);
	}

	private static boolean isBedrock(UUID player) {
		for (Predicate<UUID> detector : BEDROCK_DETECTORS) {
			try {
				if (detector.test(player)) return true;
			} catch (RuntimeException e) {
				// A detector belongs to another mod; a throw there must not end somebody's login.
				Pandorical.LOGGER.warn("[pandorical] A Bedrock detector threw for {}", player, e);
			}
		}
		return false;
	}
}
