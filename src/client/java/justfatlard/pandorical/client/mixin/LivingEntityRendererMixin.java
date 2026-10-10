package justfatlard.pandorical.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import justfatlard.pandorical.client.animation.EntityAnimations;
import justfatlard.pandorical.client.entitymodel.ClientEntityModels;
import justfatlard.pandorical.client.renderer.AnimationHolder;
import justfatlard.pandorical.client.renderer.EntityOverlayLayer;
import justfatlard.pandorical.client.renderer.EntityOverlayStore;
import justfatlard.pandorical.client.renderer.OverlayTextureHolder;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> {

	@Shadow
	protected M model;

	/** The renderer's own model, while an assigned one stands in for it during one entity's submit. */
	@Unique
	private M pandorical$ownModel;

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

		EntityAnimations.Active animation = EntityAnimations.playing(entity.getId());
		AnimationHolder holder = (AnimationHolder) state;
		holder.pandorical$setAnimation(animation);
		// One reading of the clock for the whole entity: every model posed from this state has to
		// land on the same instant, or two of them drift apart by however long the frame took.
		holder.pandorical$setAnimationElapsed(
			animation == null ? 0L : System.currentTimeMillis() - animation.startedAt());

		ClientEntityModels.extract((LivingEntityRenderer<?, ?, ?>) (Object) this, entity, state);
	}

	/**
	 * An assigned model stands in for the renderer's own for this one entity: the body, and every
	 * layer that asks the renderer for its model (held items, worn heads). Put back after, because
	 * the renderer is shared by every entity of the kind. After any subclass has picked its own
	 * model, as a renderer that chooses between two of its own does just before this.
	 */
	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At("HEAD"))
	@SuppressWarnings("unchecked")
	private void pandorical$standIn(S state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera,
			CallbackInfo ci) {
		ClientEntityModels.Drawn drawn = ClientEntityModels.drawn(state);
		if (drawn == null || drawn.model() == null) return;
		this.pandorical$ownModel = this.model;
		this.model = (M) drawn.model();
	}

	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At("RETURN"))
	private void pandorical$putBack(S state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera,
			CallbackInfo ci) {
		if (this.pandorical$ownModel == null) return;
		this.model = this.pandorical$ownModel;
		this.pandorical$ownModel = null;
	}

	/** Posed for the layers to read: with the part transforms on, so a held item finds the moved hand. */
	@WrapOperation(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Ljava/lang/Object;)V"))
	private void pandorical$poseForLayers(EntityModel<?> posed, Object state, Operation<Void> original) {
		original.call(posed, state);
		ClientEntityModels.afterPose(posed, state);
	}

	@WrapOperation(method = "getRenderType", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getTextureLocation(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Lnet/minecraft/resources/Identifier;"))
	private Identifier pandorical$assignedTexture(LivingEntityRenderer<?, ?, ?> renderer, LivingEntityRenderState state,
			Operation<Identifier> original) {
		ClientEntityModels.Drawn drawn = ClientEntityModels.drawn(state);
		return drawn != null && drawn.texture() != null ? drawn.texture() : original.call(renderer, state);
	}
}
