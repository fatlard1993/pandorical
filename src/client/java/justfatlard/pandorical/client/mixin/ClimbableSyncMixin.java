package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.content.ContentManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Climbing is decided on the client from {@code #minecraft:climbable}, whose numeric ids resolve
 * against the client registry and so cannot be relied on for server-only blocks. The server names
 * its climbable blocks directly instead. A ladder on a walkable structure - a ship at sea - is no
 * block in the world at all, so it is asked of the structure. Only ever adds a yes.
 */
@Mixin(LivingEntity.class)
public abstract class ClimbableSyncMixin {

	/** Set as vanilla's onClimbable sets it; {@code FallLocation} reads it. */
	@Shadow
	private Optional<BlockPos> lastClimbablePos;

	@Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
	private void pandorical$syncedClimbable(CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.isSpectator()) return;

		BlockPos pos = self.blockPosition();
		// A structure's ladders from the client's own copy of it, on the client only: in single
		// player this runs for the server's entities too, and the server answers for those itself.
		if (!ContentManager.isClimbable(self.level().getBlockState(pos))
				&& !(self.level().isClientSide() && justfatlard.pandorical.client.structure.StructureDecks.climbable(self))) return;

		this.lastClimbablePos = Optional.of(pos);
		cir.setReturnValue(true);
	}
}
