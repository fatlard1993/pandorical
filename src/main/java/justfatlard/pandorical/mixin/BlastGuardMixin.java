package justfatlard.pandorical.mixin;

import justfatlard.pandorical.blast.BlastGuards;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

/**
 * Takes the blocks a guard spares out of what an explosion is about to break. Here rather than where
 * the list is drawn up, so a block put down after the blast chose its area, a head say, is asked about.
 */
@Mixin(ServerExplosion.class)
public abstract class BlastGuardMixin {
	@Shadow @Final private ServerLevel level;

	@ModifyVariable(method = "interactWithBlocks", at = @At("HEAD"), argsOnly = true)
	private List<BlockPos> pandorical$spareGuarded(List<BlockPos> positions) {
		return BlastGuards.withoutSpared(this.level, positions);
	}
}
