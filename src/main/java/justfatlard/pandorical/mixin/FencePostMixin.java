package justfatlard.pandorical.mixin;

import justfatlard.pandorical.BlockMarkLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FenceBlock.class)
public abstract class FencePostMixin {
	@Inject(method = "updateShape", at = @At("RETURN"), cancellable = true)
	private void pandorical$standAlone(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
			Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random,
			CallbackInfoReturnable<BlockState> cir) {
		if (direction.getAxis().isVertical()) return;
		if (!BlockMarkLookup.isPost(level, pos) && !BlockMarkLookup.isPost(level, neighborPos)) return;
		cir.setReturnValue(cir.getReturnValue().setValue(FenceBlock.PROPERTY_BY_DIRECTION.get(direction), false));
	}

	/** Needed beside updateShape: a placement updates the neighbours, not the placed block. */
	@Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true)
	private void pandorical$placedBesidePosts(BlockPlaceContext context, CallbackInfoReturnable<BlockState> cir) {
		BlockState state = cir.getReturnValue();
		if (state == null) return;
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (BlockMarkLookup.isPost(context.getLevel(), context.getClickedPos().relative(direction))) {
				state = state.setValue(FenceBlock.PROPERTY_BY_DIRECTION.get(direction), false);
			}
		}
		cir.setReturnValue(state);
	}
}
