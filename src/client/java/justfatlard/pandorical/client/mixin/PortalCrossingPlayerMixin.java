package justfatlard.pandorical.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import justfatlard.pandorical.client.portal.Crossing;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** In an immersive portal the swirl fills over the server's short wait, and stepping in squelches. */
@Mixin(LocalPlayer.class)
public abstract class PortalCrossingPlayerMixin {
	@ModifyConstant(method = "handlePortalTransitionEffect", constant = @Constant(floatValue = 0.0125F))
	private float pandorical$thicken(float vanilla) {
		return Crossing.active() ? Crossing.thickening() : vanilla;
	}

	@WrapOperation(method = "handlePortalTransitionEffect", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/resources/sounds/SimpleSoundInstance;forLocalAmbience(Lnet/minecraft/sounds/SoundEvent;FF)Lnet/minecraft/client/resources/sounds/SimpleSoundInstance;"))
	private SimpleSoundInstance pandorical$squelchIn(SoundEvent sound, float pitch, float volume, Operation<SimpleSoundInstance> original) {
		return Crossing.active() ? Crossing.squelchIn() : original.call(sound, pitch, volume);
	}
}
