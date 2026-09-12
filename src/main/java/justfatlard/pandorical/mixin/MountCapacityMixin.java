package justfatlard.pandorical.mixin;

import justfatlard.pandorical.MountPolicy;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** On {@code Entity}: horses inherit vanilla's {@code canAddPassenger} without overriding it. */
@Mixin(Entity.class)
public abstract class MountCapacityMixin {

	@Inject(method = "canAddPassenger", at = @At("HEAD"), cancellable = true)
	private void pandorical$roomForTwo(Entity passenger, CallbackInfoReturnable<Boolean> cir) {
		if (!MountPolicy.doubleRiders()) return;
		if (!((Object) this instanceof AbstractHorse horse)) return;

		cir.setReturnValue(horse.getPassengers().size() < 2);
	}
}
