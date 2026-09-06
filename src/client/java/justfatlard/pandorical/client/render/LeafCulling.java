package justfatlard.pandorical.client.render;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Which leaf faces are deep enough inside a canopy to stop drawing.
 *
 * <p>The rule keeps a one-block shell. A face between two leaves is dropped only when the leaf on
 * the far side is itself walled in on all six sides - so the outermost layer of every tree still
 * draws exactly as Fancy always did, and only what is behind it goes. Culling every leaf-to-leaf
 * face instead would be simpler and is what the naive version does, but it lets you see daylight
 * through the gaps in the outer leaves, because there is then nothing behind them to see.
 */
public final class LeafCulling {
	private LeafCulling() {}

	/**
	 * Whether the server this client is on asks for it, independent of what the player chose.
	 *
	 * <p>Additive: a server can turn this on for content that needs it - a world of enormous trees
	 * - and cannot turn it off for somebody who wanted it. Dropped on disconnect, so it is a fact
	 * about being on that server rather than an edit to the player's settings.
	 */
	private static volatile boolean enforcedByServer = false;

	public static void setEnforced(boolean enforced) {
		enforcedByServer = enforced;
	}

	public static void onDisconnect() {
		enforcedByServer = false;
	}

	/** The player's own answer, or the server's, whichever says yes. */
	public static boolean enabled() {
		return enforcedByServer || RenderSettings.cullLeaves();
	}

	/** Whether this face sits between two leaves with more leaves beyond it. */
	public static boolean isBuriedLeafFace(BlockGetter level, BlockState state, BlockPos neighbourPos) {
		if (!enabled()) return false;
		if (!(state.getBlock() instanceof LeavesBlock)) return false;

		BlockState neighbour = level.getBlockState(neighbourPos);
		if (!(neighbour.getBlock() instanceof LeavesBlock)) return false;

		return isWalledIn(level, neighbourPos);
	}

	/**
	 * Every side of this block is leaves or something solid.
	 *
	 * <p>Logs count: the branch a leaf is packed against hides it just as well as more leaves do,
	 * and a crown built round a thickened ancient trunk is mostly leaf-against-log on the inside.
	 */
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
