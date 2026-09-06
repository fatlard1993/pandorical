package justfatlard.pandorical.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import justfatlard.pandorical.client.renderer.EntityOverlayStore;
import justfatlard.pandorical.client.renderer.OverlayRenderState;
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

/**
 * A minecart wears the texture the server set for it, the way a living entity already does.
 *
 * <p>Vanilla draws every cart from one texture, a private constant read once inside
 * {@code submit}. The entity is known while the state is extracted, so the overlay is looked
 * up then and stashed on the state; the constant read is then answered with the stash when
 * there is one. A copper minecart is the case: its weathering is a texture and nothing else.
 */
@Mixin(AbstractMinecartRenderer.class)
public abstract class AbstractMinecartRendererMixin {
	@Shadow @Final private static Identifier MINECART_LOCATION;

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/vehicle/minecart/AbstractMinecart;Lnet/minecraft/client/renderer/entity/state/MinecartRenderState;F)V",
		at = @At("TAIL"))
	private void pandorical$stashOverlay(AbstractMinecart entity, MinecartRenderState state, float partialTick, CallbackInfo ci) {
		((OverlayRenderState) state).pandorical$setOverlay(EntityOverlayStore.get(entity.getId()));
	}

	/**
	 * The state being drawn, noted as {@code submit} begins: a field read has no arguments to
	 * carry it, and one renderer draws every cart in turn on the one render thread.
	 */
	@Unique
	private MinecartRenderState pandorical$drawing;

	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/MinecartRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At("HEAD"))
	private void pandorical$noteDrawing(MinecartRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
		pandorical$drawing = state;
	}

	@Redirect(method = "submit(Lnet/minecraft/client/renderer/entity/state/MinecartRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/AbstractMinecartRenderer;MINECART_LOCATION:Lnet/minecraft/resources/Identifier;", opcode = org.objectweb.asm.Opcodes.GETSTATIC))
	private Identifier pandorical$overlayTexture() {
		Identifier overlay = pandorical$drawing == null ? null : ((OverlayRenderState) pandorical$drawing).pandorical$overlay();
		return overlay != null ? overlay : MINECART_LOCATION;
	}
}
