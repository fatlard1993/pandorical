package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.rail.ContextModels;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The chunk compiler asks {@link ContextModels} which model to draw for each block.
 *
 * <p>Two redirects on the same loop: the block-state read remembers where the compiler is,
 * and the model lookup right after it uses that. A thread-local carries the position between
 * the two because the compiler runs on worker threads, several at once.
 */
@Mixin(SectionCompiler.class)
public abstract class SectionCompilerMixin {
	@Redirect(method = "compile",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
	private BlockState pandorical$rememberWhere(RenderSectionRegion region, BlockPos pos) {
		// Every block of every section passes here; with no context models loaded there is nothing to remember
		if (ContextModels.active()) {
			ContextModels.LEVEL.set(region);
			ContextModels.POS.set(pos.immutable());
		}
		return region.getBlockState(pos);
	}

	@Redirect(method = "compile",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/BlockStateModelSet;get(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;"))
	private BlockStateModel pandorical$pickModel(BlockStateModelSet models, BlockState state) {
		return ContextModels.pick(models, state);
	}
}
