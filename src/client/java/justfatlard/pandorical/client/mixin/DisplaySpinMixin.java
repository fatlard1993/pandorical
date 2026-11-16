package justfatlard.pandorical.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import justfatlard.pandorical.client.animation.ClientDisplaySpins;
import justfatlard.pandorical.client.renderer.DisplaySpinHolder;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.state.DisplayEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Display;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A display told to spin is drawn turned, after its own transformation; see DisplaySpinApi. */
@Mixin(DisplayRenderer.class)
public abstract class DisplaySpinMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Display;Lnet/minecraft/client/renderer/entity/state/DisplayEntityRenderState;F)V",
		at = @At("TAIL"))
	private void pandorical$takeSpin(Display display, DisplayEntityRenderState state, float partialTick, CallbackInfo ci) {
		((DisplaySpinHolder) state).pandorical$setSpin(ClientDisplaySpins.turn(display.getId()));
	}

	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/DisplayEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/entity/DisplayRenderer;submitInner(Lnet/minecraft/client/renderer/entity/state/DisplayEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IF)V"))
	private void pandorical$turn(DisplayEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState camera, CallbackInfo ci) {
		Matrix4f spin = ((DisplaySpinHolder) state).pandorical$getSpin();
		if (spin != null) poseStack.mulPose(spin);
	}
}
