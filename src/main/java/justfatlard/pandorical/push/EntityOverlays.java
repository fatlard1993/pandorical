package justfatlard.pandorical.push;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.api.Capabilities;
import justfatlard.pandorical.api.EntityOverlayApi;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.protocol.EntityOverlayS2C;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Every entity's overlay texture, sent to its trackers; broadcast design mirrors StructureRegistry. */
public final class EntityOverlays implements EntityOverlayApi {
	public static final EntityOverlays INSTANCE = new EntityOverlays();

	private record OverlayEntry(Entity entity, Identifier texture) {}

	// Keyed by entity UUID; entries dropped on entity unload (see
	// handleEntityUnload). The wire protocol uses the network id, which is
	// unique per server run, so a cleared client never confuses entities.
	private final Map<UUID, OverlayEntry> overlays = new ConcurrentHashMap<>();

	private EntityOverlays() {}

	@Override
	public void set(Entity entity, Identifier texture) {
		if (entity == null || texture == null) return;
		overlays.put(entity.getUUID(), new OverlayEntry(entity, texture));
		Pandorical.LOGGER.info("Entity overlay set: {} ({}) -> {}",
			entity.getId(),
			BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()),
			texture);
		broadcastToTrackers(entity, new EntityOverlayS2C(entity.getId(), texture.toString()));
	}

	@Override
	public void clear(Entity entity) {
		if (entity == null) return;
		if (overlays.remove(entity.getUUID()) == null) return;
		broadcastToTrackers(entity, new EntityOverlayS2C(entity.getId(), ""));
	}

	/** @hidden called from Pandorical's EntityTrackingEvents.START_TRACKING handler. */
	public void handleStartTracking(Entity entity, ServerPlayer player) {
		if (!PandoricalApi.hasCapability(player, Capabilities.ENTITY_OVERLAYS)) return;
		OverlayEntry entry = overlays.get(entity.getUUID());
		if (entry != null) {
			ServerPlayNetworking.send(player, new EntityOverlayS2C(entity.getId(), entry.texture().toString()));
		}
	}

	/**
	 * @hidden called after HelloC2S registers capabilities: on join, entity
	 * tracking starts before the handshake completes, so overlays for
	 * already-tracked entities must be replayed here.
	 */
	public void handlePlayerReady(ServerPlayer player) {
		if (!PandoricalApi.hasCapability(player, Capabilities.ENTITY_OVERLAYS)) return;
		for (OverlayEntry entry : overlays.values()) {
			if (PlayerLookup.tracking(entry.entity()).contains(player)) {
				ServerPlayNetworking.send(player,
					new EntityOverlayS2C(entry.entity().getId(), entry.texture().toString()));
			}
		}
	}

	/** @hidden called from Pandorical's ServerEntityEvents.ENTITY_UNLOAD handler. */
	public void handleEntityUnload(Entity entity) {
		overlays.remove(entity.getUUID());
	}

	private void broadcastToTrackers(Entity entity, CustomPacketPayload packet) {
		for (ServerPlayer player : PlayerLookup.tracking(entity)) {
			if (PandoricalApi.hasCapability(player, Capabilities.ENTITY_OVERLAYS)) {
				ServerPlayNetworking.send(player, packet);
			}
		}
	}
}
