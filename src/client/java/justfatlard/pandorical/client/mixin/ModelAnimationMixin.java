package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.animation.EntityAnimations;
import justfatlard.pandorical.client.renderer.AnimationHolder;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TAIL, so a server animation adds to the model's own pose rather than replacing it. The state
 * parameter is Object because {@code Model}'s type parameter is unbounded.
 */
@Mixin(Model.class)
public abstract class ModelAnimationMixin {

	@Shadow
	public abstract ModelPart root();

	@Inject(method = "setupAnim", at = @At("TAIL"))
	private void pandorical$playServerAnimation(Object state, CallbackInfo ci) {
		if (!(state instanceof AnimationHolder holder)) return;

		EntityAnimations.Active active = holder.pandorical$getAnimation();
		if (active == null) return;

		KeyframeAnimation animation = EntityAnimations.baked(root(), active.animation());
		if (animation == null) return;

		animation.apply(System.currentTimeMillis() - active.startedAt(), 1.0F);
	}
}
