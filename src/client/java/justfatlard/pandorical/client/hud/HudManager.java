package justfatlard.pandorical.client.hud;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.client.component.ComponentContext;
import justfatlard.pandorical.client.component.PandoricalComponent;
import justfatlard.pandorical.client.screen.ScreenHelper;
import justfatlard.pandorical.protocol.ComponentDef;
import justfatlard.pandorical.protocol.ComponentUpdate;
import justfatlard.pandorical.protocol.ShowHudS2C;
import justfatlard.pandorical.protocol.UpdateHudS2C;
import justfatlard.pandorical.protocol.HideHudS2C;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages active HUD overlays on the client.
 * Overlays are sent by the server via ShowHudS2C and rendered each frame.
 */
public final class HudManager {
	private HudManager() {}

	private static final Map<String, HudOverlay> activeOverlays = new LinkedHashMap<>();

	public static void handleShow(ShowHudS2C payload) {
		// Build component trees
		ComponentContext context = new ComponentContext(
			payload.overlayId(), "hud",
			0, 0, // HUD overlays use absolute positioning via anchor/offset
			Minecraft.getInstance().font,
			(componentId, data) -> {}, // HUD components don't send actions
			null // no menu for HUD overlays
		);

		List<PandoricalComponent> roots = new ArrayList<>();
		Map<String, PandoricalComponent> componentIndex = new HashMap<>();

		for (ComponentDef def : payload.components()) {
			PandoricalComponent root = ScreenHelper.buildComponent(def, context, 0, 0, componentIndex);
			roots.add(root);
		}

		HudOverlay overlay = new HudOverlay(
			payload.overlayId(), payload.anchor(),
			payload.offsetX(), payload.offsetY(),
			roots, componentIndex
		);

		activeOverlays.put(payload.overlayId(), overlay);
		Pandorical.LOGGER.debug("HUD overlay '{}' shown with {} components", payload.overlayId(), roots.size());
	}

	public static void handleUpdate(UpdateHudS2C payload) {
		HudOverlay overlay = activeOverlays.get(payload.overlayId());
		if (overlay == null) return;
		ScreenHelper.applyUpdates(payload.updates(), overlay.componentIndex);
	}

	public static void handleHide(HideHudS2C payload) {
		activeOverlays.remove(payload.overlayId());
	}

	public static Map<String, HudOverlay> getActiveOverlays() {
		return activeOverlays;
	}

	public static void clear() {
		activeOverlays.clear();
	}
}
