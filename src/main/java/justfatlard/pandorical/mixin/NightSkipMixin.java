package justfatlard.pandorical.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import justfatlard.pandorical.sleep.SleepSpeed;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** With sleeping speeding the night up, enough players asleep no longer skips it to morning. */
@Mixin(ServerLevel.class)
public abstract class NightSkipMixin {

	@ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/server/players/SleepStatus;areEnoughDeepSleeping(ILjava/util/List;)Z"))
	private boolean pandorical$speedInsteadOfSkip(boolean enoughAsleep) {
		return enoughAsleep && !SleepSpeed.replacesSkip((ServerLevel) (Object) this);
	}
}
