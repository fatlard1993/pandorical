package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.skin.SkinOverrides;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooked downstream of the profile because authlib resolves profile skins only from texture URLs
 * on Mojang's domains, so a server cannot put its own image there.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class PlayerSkinOverrideMixin {

	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void pandorical$wearOverride(CallbackInfoReturnable<PlayerSkin> cir) {
		AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;

		PlayerSkin worn = SkinOverrides.forPlayer(self.getUUID(), cir.getReturnValue());
		if (worn != null) cir.setReturnValue(worn);
	}
}
