package justfatlard.pandorical.push;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import justfatlard.pandorical.api.Capabilities;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.SkinApi;
import justfatlard.pandorical.protocol.SkinOverrideS2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Every skin override in force, broadcast as it changes and replayed to each arrival. */
public final class SkinOverrides implements SkinApi {
	public static final SkinOverrides INSTANCE = new SkinOverrides();

	/**
	 * Every override currently in force, so a player who joins later still sees them.
	 *
	 * <p>A skin is not an event, it is a state, and a client that missed the announcement would
	 * otherwise see that person as Steve for as long as both stayed logged in. Held by subject
	 * so a second call about the same player replaces the first rather than piling up.
	 */
	private static final Map<UUID, SkinOverrideS2C> WORN = new ConcurrentHashMap<>();

	/**
	 * Subjects dressed for the life of the server: a leaver's row goes unless it is one of these.
	 * The two lifetimes are told apart here rather than by two maps, so the one send loop and
	 * the one replay on join serve both.
	 */
	private static final Set<UUID> KEPT = ConcurrentHashMap.newKeySet();

	private SkinOverrides() {}

	@Override
	public void set(ServerPlayer subject, byte[] png, boolean slim) {
		if (png == null || png.length == 0) {
			clear(subject);
			return;
		}
		broadcast(subject, new SkinOverrideS2C(subject.getUUID(), png, slim));
	}

	@Override
	public void clear(ServerPlayer subject) {
		clear(subject.level().getServer(), subject.getUUID());
	}

	@Override
	public void set(MinecraftServer server, UUID subject, byte[] png, boolean slim) {
		if (png == null || png.length == 0) {
			clear(server, subject);
			return;
		}
		KEPT.add(subject);
		SkinOverrideS2C worn = new SkinOverrideS2C(subject, png, slim);
		WORN.put(subject, worn);
		send(server, worn);
	}

	@Override
	public void clear(MinecraftServer server, UUID subject) {
		KEPT.remove(subject);
		WORN.remove(subject);
		// An empty image is how "wear your own skin again" is said; the alternative would be a
		// second packet type that means nothing else.
		send(server, new SkinOverrideS2C(subject, new byte[0], false));
	}

	private void broadcast(ServerPlayer subject, SkinOverrideS2C worn) {
		WORN.put(subject.getUUID(), worn);
		send(subject.level().getServer(), worn);
	}

	private static void send(MinecraftServer server, SkinOverrideS2C worn) {
		if (server == null) return;
		for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
			if (!PandoricalApi.hasCapability(viewer, Capabilities.SKINS)) continue;
			ServerPlayNetworking.send(viewer, worn);
		}
	}

	/** Catch a newly arrived client up on everyone already wearing something. */
	public static void sendAllTo(ServerPlayer viewer) {
		if (WORN.isEmpty() || !PandoricalApi.hasCapability(viewer, Capabilities.SKINS)) return;
		for (SkinOverrideS2C worn : WORN.values()) {
			ServerPlayNetworking.send(viewer, worn);
		}
	}

	/** Drop a leaver's entry, so the map does not grow for the life of the server. */
	public static void forget(UUID subject) {
		if (!KEPT.contains(subject)) WORN.remove(subject);
	}
}
