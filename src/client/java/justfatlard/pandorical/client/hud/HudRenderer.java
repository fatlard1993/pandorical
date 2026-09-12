package justfatlard.pandorical.client.hud;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.client.component.PandoricalComponent;
import justfatlard.pandorical.client.mixin.PlayerTabOverlayAccessor;
import justfatlard.pandorical.client.screen.ScreenHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

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
		boolean tabListShowing = ((PlayerTabOverlayAccessor) mc.gui.hud.getTabList()).pandorical$visible();

		for (HudOverlay overlay : HudManager.getActiveOverlays().values()) {
			if (tabListShowing && yieldsToTabList(overlay)) continue;
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

	/** The player list covers the top centre whenever vanilla shows it. */
	private static boolean yieldsToTabList(HudOverlay overlay) {
		return "top_center".equals(overlay.anchor);
	}

	private static int resolveX(HudOverlay overlay, int guiWidth) {
		return switch (overlay.anchor) {
			case "top_right", "bottom_right" -> guiWidth - overlay.offsetX - overlay.getWidth();
			case "center" -> (guiWidth - overlay.getWidth()) / 2 + overlay.offsetX;
			case "bottom_center" -> guiWidth / 2 + overlay.offsetX;
			case "top_center" -> (guiWidth - overlay.getWidth()) / 2 + overlay.offsetX;
			default -> overlay.offsetX;
		};
	}

	private static int resolveY(HudOverlay overlay, int guiHeight) {
		return switch (overlay.anchor) {
			case "bottom_left", "bottom_right", "bottom_center" -> guiHeight - overlay.offsetY - overlay.getHeight();
			case "center" -> (guiHeight - overlay.getHeight()) / 2 + overlay.offsetY;
			default -> overlay.offsetY;
		};
	}
}
