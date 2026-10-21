package justfatlard.pandorical.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import justfatlard.pandorical.client.portal.Crossing;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.progress.ChunkLoadStatusView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The change of world through an immersive portal is the swirl and nothing on it: the loading
 * screen keeps vanilla's portal background and drops its words, its bar and its map of chunks.
 */
@Mixin(LevelLoadingScreen.class)
public abstract class PortalCrossingScreenMixin {
	@Shadow
	private LevelLoadingScreen.Reason reason;

	private boolean pandorical$bare() {
		return reason == LevelLoadingScreen.Reason.NETHER_PORTAL && Crossing.active();
	}

	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;centeredText(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"))
	private void pandorical$noWords(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color, Operation<Void> original) {
		if (!pandorical$bare()) original.call(graphics, font, text, x, y, color);
	}

	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screens/LevelLoadingScreen;drawProgressBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIIF)V"))
	private void pandorical$noBar(LevelLoadingScreen screen, GuiGraphicsExtractor graphics, int left, int top, int width, int height,
			float progress, Operation<Void> original) {
		if (!pandorical$bare()) original.call(screen, graphics, left, top, width, height, progress);
	}

	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screens/LevelLoadingScreen;extractChunksForRendering(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIILnet/minecraft/server/level/progress/ChunkLoadStatusView;)V"))
	private void pandorical$noChunks(GuiGraphicsExtractor graphics, int x, int y, int size, int margin, ChunkLoadStatusView view,
			Operation<Void> original) {
		if (!pandorical$bare()) original.call(graphics, x, y, size, margin, view);
	}
}
