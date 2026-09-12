package justfatlard.pandorical.client.render;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A leaf-to-leaf face is culled only when the far leaf is walled in on all six sides, so a canopy
 * keeps a one-block shell; culling every such face shows daylight through its gaps.
 */
public final class LeafCulling {
	private LeafCulling() {}

	private static volatile boolean enforcedByServer = false;

	public static void setEnforced(boolean enforced) {
		enforcedByServer = enforced;
	}

	public static void onDisconnect() {
		enforcedByServer = false;
	}

	public static boolean enabled() {
		return enforcedByServer || RenderSettings.cullLeaves();
	}

	public static boolean isBuriedLeafFace(BlockGetter level, BlockState state, BlockPos neighbourPos) {
		if (!enabled()) return false;
		if (!(state.getBlock() instanceof LeavesBlock)) return false;

		BlockState neighbour = level.getBlockState(neighbourPos);
		if (!(neighbour.getBlock() instanceof LeavesBlock)) return false;

		return isWalledIn(level, neighbourPos);
	}

	private static boolean isWalledIn(BlockGetter level, BlockPos pos) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

		for (Direction direction : Direction.values()) {
			BlockState side = level.getBlockState(cursor.setWithOffset(pos, direction));
			if (side.getBlock() instanceof LeavesBlock) continue;
			if (side.isSolidRender()) continue;
			return false;
		}
		return true;
	}
}
