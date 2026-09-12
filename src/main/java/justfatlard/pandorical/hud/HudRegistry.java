package justfatlard.pandorical.hud;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.api.Capabilities;
import justfatlard.pandorical.api.HudApi;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.protocol.ComponentUpdate;
import justfatlard.pandorical.protocol.HideHudS2C;
import justfatlard.pandorical.protocol.SetVanillaHudElementsS2C;
import justfatlard.pandorical.protocol.ShowHudS2C;
import justfatlard.pandorical.protocol.UpdateHudS2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class HudRegistry implements HudApi {
	public static final HudRegistry INSTANCE = new HudRegistry();

	private HudRegistry() {}

	@Override
	public void show(ServerPlayer player, ShowHudS2C overlay) {
		if (!PandoricalApi.hasCapability(player, Capabilities.HUD)) {
			Pandorical.LOGGER.warn(
				"Cannot show HUD for {} — client does not support HUD rendering (not yet implemented on client)",
				player.getName().getString());
			return;
		}
		ServerPlayNetworking.send(player, overlay);
	}

	@Override
	public void update(ServerPlayer player, String overlayId, List<ComponentUpdate> updates) {
		if (!PandoricalApi.isAvailable(player)) return;
		ServerPlayNetworking.send(player, new UpdateHudS2C(overlayId, updates));
	}

	@Override
	public void hide(ServerPlayer player, String overlayId) {
		if (!PandoricalApi.isAvailable(player)) return;
		ServerPlayNetworking.send(player, new HideHudS2C(overlayId));
	}

	/** player UUID to owner id to that owner's requested element ids. */
	private final Map<UUID, Map<String, Set<String>>> hiddenVanillaElements = new ConcurrentHashMap<>();

	@Override
	public void hideVanillaElements(ServerPlayer player, String ownerId, Collection<String> elementIds) {
		if (!PandoricalApi.hasCapability(player, Capabilities.HUD_ELEMENTS)) return;
		Map<String, Set<String>> byOwner = hiddenVanillaElements
			.computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>());
		if (elementIds.isEmpty()) {
			byOwner.remove(ownerId);
		} else {
			byOwner.put(ownerId, Set.copyOf(elementIds));
		}
		sendVanillaElementSet(player, byOwner);
	}

	@Override
	public void restoreVanillaElements(ServerPlayer player, String ownerId) {
		Map<String, Set<String>> byOwner = hiddenVanillaElements.get(player.getUUID());
		if (byOwner == null || byOwner.remove(ownerId) == null) return;
		if (!PandoricalApi.hasCapability(player, Capabilities.HUD_ELEMENTS)) return;
		sendVanillaElementSet(player, byOwner);
	}

	private static void sendVanillaElementSet(ServerPlayer player, Map<String, Set<String>> byOwner) {
		Set<String> union = new LinkedHashSet<>();
		for (Set<String> ids : byOwner.values()) union.addAll(ids);
		ServerPlayNetworking.send(player, new SetVanillaHudElementsS2C(List.copyOf(union)));
	}

	public void forgetPlayer(UUID uuid) {
		hiddenVanillaElements.remove(uuid);
	}
}
