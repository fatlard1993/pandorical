package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.animation.EntityAnimations;
import justfatlard.pandorical.client.renderer.AnimationHolder;
import justfatlard.pandorical.client.renderer.EntityOverlayLayer;
import justfatlard.pandorical.client.renderer.EntityOverlayStore;
import justfatlard.pandorical.client.renderer.OverlayTextureHolder;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> {

	@Shadow
	protected abstract boolean addLayer(RenderLayer<S, M> layer);

	@Inject(method = "<init>", at = @At("TAIL"))
	@SuppressWarnings("unchecked")
	private void pandorical$addOverlayLayer(EntityRendererProvider.Context context, M model,
			float shadowRadius, CallbackInfo ci) {
		this.addLayer(new EntityOverlayLayer<>((RenderLayerParent<S, M>) this));
	}

	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL")
	)
	private void pandorical$extractOverlay(T entity, S state, float partialTick, CallbackInfo ci) {
		// Both written every time, null included: render states are pooled and reused.
		((OverlayTextureHolder) state).pandorical$setOverlayTexture(
			EntityOverlayStore.get(entity.getId()));

		((AnimationHolder) state).pandorical$setAnimation(
			EntityAnimations.playing(entity.getId()));
	}
}
