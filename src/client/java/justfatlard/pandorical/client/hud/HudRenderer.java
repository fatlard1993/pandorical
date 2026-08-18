package justfatlard.pandorical.client.hud;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.client.component.PandoricalComponent;
import justfatlard.pandorical.client.screen.ScreenHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

/**
 * Renders all active HUD overlays.
 */
public final class HudRenderer {
	private HudRenderer() {}

	private static final Identifier ID = Identifier.fromNamespaceAndPath("pandorical", "hud_overlays");

	public static void register() {
		HudElementRegistry.addLast(ID, HudRenderer::render);
		Pandorical.LOGGER.info("Pandorical HUD renderer registered");
	}

	private static void render(GuiGraphicsExtractor context, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gui.screen() != null) return;

		int guiWidth = context.guiWidth();
		int guiHeight = context.guiHeight();

		for (HudOverlay overlay : HudManager.getActiveOverlays().values()) {
			int baseX = resolveX(overlay, guiWidth);
			int baseY = resolveY(overlay, guiHeight);

			Matrix3x2fStack pose = context.pose();
			pose.pushMatrix();
			pose.translate(baseX, baseY);

			for (PandoricalComponent root : overlay.roots) {
				ScreenHelper.renderComponentTree(root, context, 0, 0, delta.getGameTimeDeltaPartialTick(true));
			}

			pose.popMatrix();
		}
	}

	private static int resolveX(HudOverlay overlay, int guiWidth) {
		return switch (overlay.anchor) {
			case "top_right", "bottom_right" -> guiWidth - overlay.offsetX - overlay.getWidth();
			// "center" treats offsetX as a pixel nudge away from true horizontal center, rather than
			// a corner margin; lets overlays sit near the crosshair (e.g. a telegraphed prompt)
			// where none of the four corner anchors can reach.
			case "center" -> (guiWidth - overlay.getWidth()) / 2 + overlay.offsetX;
			// "bottom_center" treats offsetX as the signed position of the overlay's LEFT edge
			// relative to horizontal center: the vanilla hotbar status rows (health, hunger, air)
			// are laid out center-relative, so overlays meant to sit with them anchor the same way.
			case "bottom_center" -> guiWidth / 2 + overlay.offsetX;
			// "top_center" centres the overlay's own width, so offsetX is a nudge
			// rather than an edge: an overlay whose width changes with its contents
			// stays put instead of drifting sideways as it grows.
			case "top_center" -> (guiWidth - overlay.getWidth()) / 2 + overlay.offsetX;
			default -> overlay.offsetX; // top_left, bottom_left, or unrecognized
		};
	}

	private static int resolveY(HudOverlay overlay, int guiHeight) {
		return switch (overlay.anchor) {
			case "bottom_left", "bottom_right", "bottom_center" -> guiHeight - overlay.offsetY - overlay.getHeight();
			case "center" -> (guiHeight - overlay.getHeight()) / 2 + overlay.offsetY;
			default -> overlay.offsetY; // top_left, top_right, top_center, or unrecognized
		};
	}
}
