package justfatlard.pandorical.client.mixin;

import java.util.Optional;
import justfatlard.pandorical.client.content.ContentManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets a server-only block be climbed.
 *
 * <p>Whether a player climbs is decided here, on the client, and vanilla decides it by asking
 * whether the block is in {@code #minecraft:climbable}. That works for blocks the client has always
 * known about. It is a thin thread to hang on for a block that only entered the client's registry
 * when it connected: tag membership arrives as numeric ids resolved against that registry, and the
 * server's copy of it holds forty mods' worth of blocks the client will never have.
 *
 * <p>So the server states the fact directly and this consults it. Additive: everything vanilla
 * already considers climbable still is, and this only ever answers yes for a block the server
 * named. If the tag does arrive intact, this agrees with it.
 */
@Mixin(LivingEntity.class)
public abstract class ClimbableSyncMixin {

	/**
	 * Where vanilla remembers the last thing climbed, so that stepping off one breaks the fall the
	 * way stepping off a ladder does. Set here too, or a beanstalk would be the one climbable in
	 * the game that hurts to leave.
	 */
	@Shadow
	private Optional<BlockPos> lastClimbablePos;

	// Required explicitly: this config lets mixins fail quietly by default, and a climb that stops
	// working is indistinguishable from a climb that was never wired up.
	@Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true, require = 1)
	private void pandorical$syncedClimbable(CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.isSpectator()) return;

		BlockPos pos = self.blockPosition();
		if (!ContentManager.isClimbable(self.level().getBlockState(pos))) return;

		this.lastClimbablePos = Optional.of(pos);
		cir.setReturnValue(true);
	}
}
