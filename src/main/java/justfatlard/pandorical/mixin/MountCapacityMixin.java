package justfatlard.pandorical.mixin;

import justfatlard.pandorical.MountPolicy;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * How many people a mount will take.
 *
 * <p>On {@code Entity} because that is where vanilla decides it - one line saying a thing is full
 * once its passenger list is not empty - and a horse never overrode it. Narrowed back to horses
 * here rather than by choosing a target class, so nothing else in the game quietly gains a second
 * seat.
 */
@Mixin(Entity.class)
public abstract class MountCapacityMixin {

	@Inject(method = "canAddPassenger", at = @At("HEAD"), cancellable = true)
	private void pandorical$roomForTwo(Entity passenger, CallbackInfoReturnable<Boolean> cir) {
		if (!MountPolicy.doubleRiders()) return;
		if (!((Object) this instanceof AbstractHorse horse)) return;

		cir.setReturnValue(horse.getPassengers().size() < 2);
	}
}
