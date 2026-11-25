package justfatlard.pandorical.mixin;

import justfatlard.pandorical.push.EntityOrientations;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A turned vehicle's riders sit turned with it, on the server and on the client alike: each side
 * seats riders for itself, so both have to agree on where.
 */
@Mixin(Entity.class)
public abstract class OrientedRiderMixin {
	@Inject(method = "getPassengerRidingPosition", at = @At("RETURN"), cancellable = true)
	private void pandorical$seatTurned(Entity passenger, CallbackInfoReturnable<Vec3> cir) {
		Entity vehicle = (Entity) (Object) this;
		Quaternionfc orientation = EntityOrientations.of(vehicle);
		if (orientation != null) cir.setReturnValue(EntityOrientations.seat(vehicle, passenger, cir.getReturnValue(), orientation));
	}
}
