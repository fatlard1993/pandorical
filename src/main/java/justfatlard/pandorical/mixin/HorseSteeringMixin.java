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

	/** Degrees per tick at full lock, close to a boat's. */
	private static final float TURN_RATE = 4.0F;

	@Inject(method = "getRiddenRotation", at = @At("HEAD"), cancellable = true)
	private void pandorical$steerLikeABoat(LivingEntity rider, CallbackInfoReturnable<Vec2> cir) {
		if (!MountPolicy.freeLook()) return;

		AbstractHorse horse = (AbstractHorse) (Object) this;
		float turn = rider instanceof Player player ? player.xxa : 0.0F;

		cir.setReturnValue(new Vec2(horse.getXRot(), horse.getYRot() + turn * TURN_RATE));
	}

	@Inject(method = "getRiddenInput", at = @At("RETURN"), cancellable = true)
	private void pandorical$noStrafing(Player rider, Vec3 input, CallbackInfoReturnable<Vec3> cir) {
		if (!MountPolicy.freeLook()) return;

		// Strafe turns now, so it must not also slide.
		Vec3 moved = cir.getReturnValue();
		cir.setReturnValue(new Vec3(0.0, moved.y, moved.z));
	}
}
