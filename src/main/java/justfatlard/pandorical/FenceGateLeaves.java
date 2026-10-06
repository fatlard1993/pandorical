package justfatlard.pandorical;

import justfatlard.pandorical.api.BlockMarkApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.BiPredicate;

/**
 * Which piece of an open wide gate's leaf a square is, read the same way wherever it is asked:
 * by the client to draw it, and by the gate's outline and collision on either side.
 *
 * <p>A wide gate opens by moving its blocks out along its leaves, and each square marked
 * {@link BlockMarkApi#GATE_WIDE} is one piece: {@code hinge} where the leaf starts with more of it
 * in front, {@code hinge_tip} where it starts and ends in one square, {@code seg} a square swung
 * out with more in front, {@code tip} the last. Every answer reads one block away at most.
 */
public final class FenceGateLeaves {
	private FenceGateLeaves() {}

	/** The post a gate hangs from, its own "left" or "right", or null for two leaves. */
	public static String side(BiPredicate<BlockPos, String> marks, BlockPos pos) {
		if (marks.test(pos, BlockMarkApi.GATE_HINGE_LEFT)) return "left";
		if (marks.test(pos, BlockMarkApi.GATE_HINGE_RIGHT)) return "right";
		return null;
	}

	/** This open gate's piece of a wide gate's leaf, or null when it is no part of one. */
	public static String piece(BlockGetter level, BlockPos pos, BlockState state, BiPredicate<BlockPos, String> marks) {
		if (!state.getValue(FenceGateBlock.OPEN) || !marks.test(pos, BlockMarkApi.GATE_WIDE)) return null;
		String side = side(marks, pos);
		if (side == null) return null;
		Direction facing = state.getValue(FenceGateBlock.FACING);
		boolean behind = more(level, pos.relative(facing.getOpposite()), state, side, marks);
		boolean ahead = more(level, pos.relative(facing), state, side, marks);
		return behind ? (ahead ? "seg" : "tip") : (ahead ? "hinge" : "hinge_tip");
	}

	/** Whether this square is more of the same leaf. */
	private static boolean more(BlockGetter level, BlockPos at, BlockState mine, String side, BiPredicate<BlockPos, String> marks) {
		BlockState other = level.getBlockState(at);
		return other.getBlock() == mine.getBlock()
			&& other.getValue(FenceGateBlock.OPEN)
			&& other.getValue(FenceGateBlock.FACING) == mine.getValue(FenceGateBlock.FACING)
			&& marks.test(at, BlockMarkApi.GATE_WIDE)
			&& side.equals(side(marks, at));
	}
}
