package justfatlard.pandorical.mixin;

import justfatlard.pandorical.BlockMarkLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WallSide;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A wall marked as a post joins nothing, and nothing joins to it; it always shows its pillar. */
@Mixin(WallBlock.class)
public abstract class WallPostMixin {
	@Inject(method = "updateShape(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/world/level/ScheduledTickAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/util/RandomSource;)Lnet/minecraft/world/level/block/state/BlockState;",
		at = @At("RETURN"), cancellable = true)
	private void pandorical$standAlone(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
			Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random,
			CallbackInfoReturnable<BlockState> cir) {
		if (direction.getAxis().isVertical()) return;
		if (!BlockMarkLookup.isPost(level, pos) && !BlockMarkLookup.isPost(level, neighborPos)) return;
		cir.setReturnValue(cir.getReturnValue()
			.setValue(WallBlock.PROPERTY_BY_DIRECTION.get(direction), WallSide.NONE)
			.setValue(WallBlock.UP, true));
	}

	@Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true)
	private void pandorical$placedBesidePosts(BlockPlaceContext context, CallbackInfoReturnable<BlockState> cir) {
		BlockState state = cir.getReturnValue();
		if (state == null) return;
		boolean changed = false;
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (BlockMarkLookup.isPost(context.getLevel(), context.getClickedPos().relative(direction))) {
				state = state.setValue(WallBlock.PROPERTY_BY_DIRECTION.get(direction), WallSide.NONE);
				changed = true;
			}
		}
		if (changed) cir.setReturnValue(state.setValue(WallBlock.UP, true));
	}
}
