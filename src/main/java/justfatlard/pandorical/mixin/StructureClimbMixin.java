package justfatlard.pandorical.mixin;

import justfatlard.pandorical.structure.StructureRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * The server's half of climbing a ladder on a walkable structure. The client climbs it from the
 * structure it draws; the server has to agree, or a long climb reads as a long fall and the
 * player lands from the top of the mast, or as hovering and they are kicked for flying.
 */
@Mixin(LivingEntity.class)
public abstract class StructureClimbMixin {
	@Shadow
	private Optional<BlockPos> lastClimbablePos;

	@Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
	private void pandorical$structureClimbable(CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!(self instanceof ServerPlayer) || self.isSpectator()) return;
		if (!StructureRegistry.INSTANCE.climbableAt(self)) return;
		this.lastClimbablePos = Optional.of(self.blockPosition());
		cir.setReturnValue(true);
	}
}
