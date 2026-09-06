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

/**
 * Steering a horse instead of aiming it.
 *
 * <p>Vanilla points a horse wherever its rider is looking, which means looking is steering: turn to
 * see what is chasing you and the horse turns into it, and a bow is useless from the saddle because
 * aiming anywhere but forward drives you there.
 *
 * <p>Two small changes make it handle like a boat. The heading the mount adopts each tick becomes
 * its own rather than the rider's, nudged by the strafe keys; and the strafe input stops sliding the
 * horse sideways, since it is doing the turning now. Everything else about riding is untouched -
 * {@code tickRidden} still applies the rotation it is given and still keeps the head, body and
 * previous yaw in step, so nothing downstream has to know this happened.
 */
@Mixin(AbstractHorse.class)
public abstract class HorseSteeringMixin {

	/**
	 * Degrees per tick at full lock.
	 *
	 * <p>Close to a boat's, which is the handling being borrowed. Fast enough to come about without
	 * planning it, slow enough that a horse still feels like an animal rather than a turret.
	 */
	private static final float TURN_RATE = 4.0F;

	@Inject(method = "getRiddenRotation", at = @At("HEAD"), cancellable = true)
	private void pandorical$steerLikeABoat(LivingEntity rider, CallbackInfoReturnable<Vec2> cir) {
		if (!MountPolicy.freeLook()) return;

		AbstractHorse horse = (AbstractHorse) (Object) this;
		float turn = rider instanceof Player player ? player.xxa : 0.0F;

		// The mount's own heading, turned; and its own pitch, which a horse never changes anyway.
		// Returning the rider's rotation is what tied the two together in the first place.
		cir.setReturnValue(new Vec2(horse.getXRot(), horse.getYRot() + turn * TURN_RATE));
	}

	@Inject(method = "getRiddenInput", at = @At("RETURN"), cancellable = true)
	private void pandorical$noStrafing(Player rider, Vec3 input, CallbackInfoReturnable<Vec3> cir) {
		if (!MountPolicy.freeLook()) return;

		// Strafe turns now, so it must not also slide. Left alone, holding left would crab the
		// horse sideways while it turned, which reads as ice rather than reins.
		Vec3 moved = cir.getReturnValue();
		cir.setReturnValue(new Vec3(0.0, moved.y, moved.z));
	}
}
