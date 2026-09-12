package justfatlard.pandorical.mixin;

import justfatlard.pandorical.MountPolicy;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractHorse.class)
public abstract class HorsePillionMixin {

	private static final double PILLION_OFFSET = 0.6;

	@Inject(method = "getPassengerAttachmentPoint", at = @At("RETURN"), cancellable = true)
	private void pandorical$seatBehind(Entity passenger, EntityDimensions dimensions, float scale,
			CallbackInfoReturnable<Vec3> cir) {
		if (!MountPolicy.doubleRiders()) return;

		AbstractHorse horse = (AbstractHorse) (Object) this;
		var riders = horse.getPassengers();
		if (riders.size() < 2 || riders.indexOf(passenger) != 1) return;

		cir.setReturnValue(cir.getReturnValue().add(0.0, 0.0, -PILLION_OFFSET * scale));
	}
}
