package justfatlard.pandorical.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import justfatlard.pandorical.client.renderer.EntityOverlayStore;
import justfatlard.pandorical.client.renderer.OverlayTextureHolder;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.AbstractMinecartRenderer;
import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.objectweb.asm.Opcodes;

/**
 * Vanilla draws every cart from the constant {@code MINECART_LOCATION}, read inside
 * {@code submit}. The overlay is looked up at extraction, where the entity is known, and answers
 * that read.
 */
@Mixin(AbstractMinecartRenderer.class)
public abstract class AbstractMinecartRendererMixin {
	@Shadow @Final private static Identifier MINECART_LOCATION;

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/vehicle/minecart/AbstractMinecart;Lnet/minecraft/client/renderer/entity/state/MinecartRenderState;F)V",
		at = @At("TAIL"))
	private void pandorical$stashOverlay(AbstractMinecart entity, MinecartRenderState state, float partialTick, CallbackInfo ci) {
		((OverlayTextureHolder) state).pandorical$setOverlayTexture(EntityOverlayStore.get(entity.getId()));
	}

	/** A field-read redirect gets no arguments; submit draws carts one at a time. */
	@Unique
	private MinecartRenderState pandorical$drawing;

	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/MinecartRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At("HEAD"))
	private void pandorical$noteDrawing(MinecartRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
		pandorical$drawing = state;
	}

	@Redirect(method = "submit(Lnet/minecraft/client/renderer/entity/state/MinecartRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/AbstractMinecartRenderer;MINECART_LOCATION:Lnet/minecraft/resources/Identifier;", opcode = Opcodes.GETSTATIC))
	private Identifier pandorical$overlayTexture() {
		Identifier overlay = pandorical$drawing == null ? null : ((OverlayTextureHolder) pandorical$drawing).pandorical$getOverlayTexture();
		return overlay != null ? overlay : MINECART_LOCATION;
	}
}
