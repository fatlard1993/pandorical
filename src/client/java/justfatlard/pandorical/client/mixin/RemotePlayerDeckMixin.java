package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.content.DeckRiderInterpolationHandler;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Other players on a walkable deck are drawn on it; see {@link DeckRiderInterpolationHandler}. */
@Mixin(LivingEntity.class)
public abstract class RemotePlayerDeckMixin {

	@Inject(method = "createInterpolationHandler", at = @At("RETURN"), cancellable = true)
	private void pandorical$rideDeck(CallbackInfoReturnable<InterpolationHandler> cir) {
		if ((Object) this instanceof RemotePlayer player) {
			cir.setReturnValue(new DeckRiderInterpolationHandler(player, cir.getReturnValue()));
		}
	}
}
