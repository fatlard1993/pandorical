package justfatlard.pandorical.mixin;

import justfatlard.pandorical.MountPolicy;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla's {@code tickRidden} still applies the returned rotation to head, body and previous yaw. */
@Mixin(AbstractHorse.class)
public abstract class HorseSteeringMixin {

	/**
	 * Degrees per tick at full lock: a whole turn in about a second and a half.
	 *
	 * <p>It was a boat's four, which is the wrong animal. A boat is meant to feel like weight on
	 * water and takes better than two seconds to come about; a horse answers the reins, and at four
	 * it felt like steering a barge.
	 */
	private static final float TURN_RATE = 10.0F;

	@Inject(method = "getRiddenRotation", at = @At("HEAD"), cancellable = true)
	private void pandorical$steerLikeABoat(LivingEntity rider, CallbackInfoReturnable<Vec2> cir) {
		if (!MountPolicy.freeLook()) return;

		AbstractHorse horse = (AbstractHorse) (Object) this;
		float turn = rider instanceof Player player ? player.xxa : 0.0F;

		// Subtracted, because the two conventions point opposite ways: xxa is left minus right, so
		// holding left is positive, while yaw grows to the right - at yaw 0 you face south and at
		// yaw 90 you face west, which is a right turn. Added, they made the horse answer left with
		// right.
		cir.setReturnValue(new Vec2(horse.getXRot(), horse.getYRot() - turn * TURN_RATE));
	}

	@Inject(method = "getRiddenInput", at = @At("RETURN"), cancellable = true)
	private void pandorical$noStrafing(Player rider, Vec3 input, CallbackInfoReturnable<Vec3> cir) {
		if (!MountPolicy.freeLook()) return;

		// Strafe turns now, so it must not also slide.
		Vec3 moved = cir.getReturnValue();
		cir.setReturnValue(new Vec3(0.0, moved.y, moved.z));
	}
}
