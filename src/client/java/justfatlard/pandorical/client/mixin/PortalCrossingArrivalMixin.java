package justfatlard.pandorical.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import justfatlard.pandorical.client.portal.Crossing;
import net.minecraft.client.renderer.LevelEventHandler;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Coming out of an immersive portal squelches instead of whooshing. */
@Mixin(LevelEventHandler.class)
public abstract class PortalCrossingArrivalMixin {
	@WrapOperation(method = "levelEvent", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/resources/sounds/SimpleSoundInstance;forLocalAmbience(Lnet/minecraft/sounds/SoundEvent;FF)Lnet/minecraft/client/resources/sounds/SimpleSoundInstance;"))
	private SimpleSoundInstance pandorical$squelchOut(SoundEvent sound, float pitch, float volume, Operation<SimpleSoundInstance> original) {
		if (sound == SoundEvents.PORTAL_TRAVEL && Crossing.active()) return Crossing.squelchOut();
		return original.call(sound, pitch, volume);
	}
}
