package justfatlard.pandorical.push;

import justfatlard.pandorical.api.Capabilities;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.SkinApi;
import justfatlard.pandorical.protocol.SkinOverrideS2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SkinOverrides implements SkinApi {
	public static final SkinOverrides INSTANCE = new SkinOverrides();

	private static final Map<UUID, SkinOverrideS2C> WORN = new ConcurrentHashMap<>();

	/** Subjects dressed for the life of the server, whose rows outlive their leaving. */
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

	public static void sendAllTo(ServerPlayer viewer) {
		if (WORN.isEmpty() || !PandoricalApi.hasCapability(viewer, Capabilities.SKINS)) return;
		for (SkinOverrideS2C worn : WORN.values()) {
			ServerPlayNetworking.send(viewer, worn);
		}
	}

	public static void forget(UUID subject) {
		if (!KEPT.contains(subject)) WORN.remove(subject);
	}
}
