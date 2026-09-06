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

/**
 * A second seat on a horse.
 *
 * <p>Where the second person sits. The limit itself is lifted in {@code MountCapacityMixin}, on
 * {@code Entity} - which is where vanilla declares it, a horse never having had an opinion of its
 * own about how full it is.
 *
 * <p>The one who got on first still drives. That is vanilla's rule already - the controlling
 * passenger is the first in the list - and it is the right one: the pillion rider has both hands
 * free, which is the point of riding double.
 */
@Mixin(AbstractHorse.class)
public abstract class HorsePillionMixin {

	/** How far behind the saddle the second rider sits, before the mount's own scale. */
	private static final double PILLION_OFFSET = 0.6;

	@Inject(method = "getPassengerAttachmentPoint", at = @At("RETURN"), cancellable = true)
	private void pandorical$seatBehind(Entity passenger, EntityDimensions dimensions, float scale,
			CallbackInfoReturnable<Vec3> cir) {
		if (!MountPolicy.doubleRiders()) return;

		AbstractHorse horse = (AbstractHorse) (Object) this;
		var riders = horse.getPassengers();
		if (riders.size() < 2 || riders.indexOf(passenger) != 1) return;

		// Behind the driver, along the mount's own back. Scaled with the horse so a pony's pillion
		// is not sat in mid-air behind it.
		cir.setReturnValue(cir.getReturnValue().add(0.0, 0.0, -PILLION_OFFSET * scale));
	}
}
