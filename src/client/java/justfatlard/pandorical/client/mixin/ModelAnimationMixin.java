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
 * Plays a server-declared animation on top of whatever the model just did to itself.
 *
 * <p>At the tail of the model's own posing on purpose: the animation is meant to be an addition
 * rather than a replacement, so a walking animal keeps walking and an animation that only turns the
 * head leaves the legs where the game put them.
 *
 * <p>{@code Model} is generic in an unbounded parameter, so the method erases to take an Object;
 * the state is checked rather than cast blindly.
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
