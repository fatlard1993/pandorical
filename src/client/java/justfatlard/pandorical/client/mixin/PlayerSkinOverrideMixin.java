package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.skin.SkinOverrides;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Draw the skin the server asked for, where one was asked for.
 *
 * <p>Taken at the point the game asks a player what they look like, rather than anywhere in the
 * profile: a profile's skin is resolved through authlib, which only trusts texture URLs from
 * domains Mojang publishes, so a server has no way to put its own image into that answer. This is
 * downstream of all of it - by the time anything asks, the override is simply what the player is
 * wearing.
 *
 * <p>Costs one map lookup per call and returns immediately when the map is empty, which is every
 * server that never sets one.
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
