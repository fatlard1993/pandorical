package justfatlard.pandorical.mixin;

import justfatlard.pandorical.trust.TrustRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Per-player PvP, where the game asks the one being hurt whether its attacker may: arrows, blows
 * and everything else that reaches a player through {@code canHarmPlayer}. A trusted pair still
 * answers to teams, as the game would.
 */
@Mixin(ServerPlayer.class)
public abstract class PlayerTrustPvpMixin {
	@Inject(method = "canHarmPlayer", at = @At("HEAD"), cancellable = true)
	private void pandorical$trustedPvp(Player attacker, CallbackInfoReturnable<Boolean> cir) {
		if (!(attacker instanceof ServerPlayer other)) return;
		ServerPlayer self = (ServerPlayer) (Object) this;
		Boolean allowed = TrustRules.INSTANCE.pvp(self, other);
		if (allowed != null) cir.setReturnValue(allowed && self.doTeamsAllowDamage(other));
	}
}
