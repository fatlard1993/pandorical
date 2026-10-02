package justfatlard.pandorical.mixin;

import justfatlard.pandorical.api.Trust;
import justfatlard.pandorical.trust.TrustRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A burning arrow or a fire charge lights the TNT it strikes; the one who shot it needs explosives
 * for that, as they would with flint and steel in hand.
 */
@Mixin(TntBlock.class)
public abstract class TntProjectileTrustMixin {
	@Inject(method = "onProjectileHit", at = @At("HEAD"), cancellable = true)
	private void pandorical$trustedTnt(Level level, BlockState state, BlockHitResult hit, Projectile projectile, CallbackInfo ci) {
		if (projectile.getOwner() instanceof ServerPlayer player && !TrustRules.INSTANCE.may(player, Trust.EXPLOSIVES)) ci.cancel();
	}
}
