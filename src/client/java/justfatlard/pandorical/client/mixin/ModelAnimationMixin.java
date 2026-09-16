package justfatlard.pandorical.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import justfatlard.pandorical.client.animation.EntityAnimations;
import justfatlard.pandorical.client.renderer.AnimationHolder;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A server animation plays on a model once the model has posed itself, so it adds to that pose
 * rather than being written over by it.
 *
 * <p>Here, where every model is posed just before it is drawn - an entity's, and its armour's, which
 * share the state and so the animation. It used to run at the end of {@code Model.setupAnim}, which
 * a model calls first, to reset, before setting its own angles: the angles a model sets outright -
 * a leg's swing, an arm's hang - replaced the animation's, and only what no model sets survived,
 * such as the body lifting or the head tipping sideways.
 */
@Mixin(ModelFeatureRenderer.class)
public abstract class ModelAnimationMixin {

	@WrapOperation(method = "prepareModel", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V"))
	private void pandorical$playServerAnimation(Model<?> model, Object state, Operation<Void> original) {
		original.call(model, state);
		if (!(state instanceof AnimationHolder holder)) return;

		EntityAnimations.Active active = holder.pandorical$getAnimation();
		if (active == null) return;

		KeyframeAnimation animation = EntityAnimations.baked(model.root(), active.animation());
		if (animation == null) return;

		animation.apply(System.currentTimeMillis() - active.startedAt(), 1.0F);
	}
}
