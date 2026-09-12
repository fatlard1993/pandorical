package justfatlard.pandorical.client.contextmodel;

import com.mojang.math.Quadrant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import justfatlard.pandorical.client.renderer.ClientBlockMarks;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * A bank of doors drawn as one door.
 *
 * <p>Doors of one kind, hung on the same side, standing in a full rectangle, are one door: a frame
 * round the outside, the sheet stretched across the inside, one handle. Each leaf takes a model
 * that keeps only the pieces of the frame on the rectangle's edge, named
 * {@code <door>_mega_<lower|upper>_<hinge>[_open]_<flags>} with the flags saying which pieces,
 * and turned by vanilla's own rotation for its facing and swing. More Doors ships them for every
 * door in the game and every one of its own.
 *
 * <p>The rectangle is read from the world at render time, so a leaf added or taken away rejoins
 * or splits the door with the next chunk rebuild.
 */
@Environment(EnvType.CLIENT)
public final class DoorBanks implements ContextModels.Provider {
	private record Key(Block block, DoubleBlockHalf half, DoorHingeSide hinge, boolean open, String flags, Direction facing) {}

	private static final Map<Key, ExtraModelKey<BlockStateModel>> KEYS = new HashMap<>();
	/** The mark More Doors puts on a leaf its owner cut loose from the leaves beside it. */
	private static final String DETACHED = "more-doors:detached";
	/** The mark More Doors puts on a leaf that slides: nothing to turn, so no knob. */
	private static final String SLIDING = "more-doors:sliding";
	private static final int MAX_LEAVES = 64;

	/** Every flag string the generator writes, per half. */
	private static final String[] LOWER_FLAGS = {"x", "h", "b", "bh", "r", "rh", "rb", "rbh",
		"l", "lh", "lb", "lbh", "lr", "lrh", "lrb", "lrbh"};
	private static final String[] UPPER_FLAGS = {"x", "t", "r", "rt", "l", "lt", "lr", "lrt"};

	@Override
	public void scan(ResourceManager resources, ContextModels.Registrar add) {
		KEYS.clear();
		for (Block block : BuiltInRegistries.BLOCK) {
			if (!isDoor(block.defaultBlockState())) continue;
			Identifier id = BuiltInRegistries.BLOCK.getKey(block);
			for (DoubleBlockHalf half : DoubleBlockHalf.values()) {
				String[] flagSet = half == DoubleBlockHalf.LOWER ? LOWER_FLAGS : UPPER_FLAGS;
				for (DoorHingeSide hinge : DoorHingeSide.values()) {
					for (boolean open : new boolean[] {false, true}) {
						for (String flags : flagSet) {
							String path = id.getPath() + "_mega_" + (half == DoubleBlockHalf.LOWER ? "bottom" : "top")
								+ "_" + hinge.getSerializedName() + (open ? "_open" : "") + "_" + flags;
							Identifier file = Identifier.fromNamespaceAndPath(id.getNamespace(), "models/block/" + path + ".json");
							if (resources.getResource(file).isEmpty()) continue;
							Identifier model = Identifier.fromNamespaceAndPath(id.getNamespace(), "block/" + path);
							for (Direction facing : Direction.Plane.HORIZONTAL) {
								KEYS.put(new Key(block, half, hinge, open, flags, facing),
									add.add(model, new Variant.SimpleModelState(Quadrant.R0, turn(facing, hinge, open), Quadrant.R0, false)
										.asModelState()));
							}
						}
					}
				}
			}
		}
	}

	/** Vanilla's rotation for a door: a quarter turn per facing, and one more either way when open. */
	private static Quadrant turn(Direction facing, DoorHingeSide hinge, boolean open) {
		int y = switch (facing) {
			case EAST -> 0;
			case SOUTH -> 90;
			case WEST -> 180;
			default -> 270;
		};
		if (open) y += hinge == DoorHingeSide.LEFT ? 90 : 270;
		return Quadrant.parseJson(y % 360);
	}

	/** The world direction of the model's low-z end for a closed door, which is where a left hinge hangs. */
	private static Direction lowEnd(Direction facing) {
		return switch (facing) {
			case EAST -> Direction.NORTH;
			case SOUTH -> Direction.EAST;
			case WEST -> Direction.SOUTH;
			default -> Direction.WEST;
		};
	}

	@Override
	public BlockStateModel pick(BlockState state, BlockAndTintGetter level, BlockPos pos, ContextModels.Lookup models) {
		if (KEYS.isEmpty() || !isDoor(state)) return null;
		DoubleBlockHalf half = state.getValue(DoorBlock.HALF);
		BlockPos foot = half == DoubleBlockHalf.LOWER ? pos : pos.below();
		BlockState footState = level.getBlockState(foot);
		if (!footState.is(state.getBlock())) return null;

		Direction facing = state.getValue(DoorBlock.FACING);
		DoorHingeSide hinge = state.getValue(DoorBlock.HINGE);
		boolean open = state.getValue(DoorBlock.OPEN);

		// Leaves run along the wall while the door is closed, and out from it once it has swung.
		Direction along = open ? facing : facing.getClockWise();
		// A leaf cut loose from its neighbours is its own door, however they agree. A door of one
		// leaf is vanilla's to draw, unless it slides: then it is drawn here, without a knob.
		boolean sliding = ClientBlockMarks.has(foot, SLIDING);
		Set<BlockPos> leaves = ClientBlockMarks.has(foot, DETACHED) ? Set.of(foot) : bank(level, foot, footState, along, sliding);
		if (leaves.size() < 2 && !sliding) return null;

		// A full rectangle, measured along the leaves and up in door heights.
		int sMin = Integer.MAX_VALUE, sMax = Integer.MIN_VALUE, rMin = Integer.MAX_VALUE, rMax = Integer.MIN_VALUE;
		for (BlockPos leaf : leaves) {
			int s = (leaf.getX() - foot.getX()) * along.getStepX() + (leaf.getZ() - foot.getZ()) * along.getStepZ();
			int r = (leaf.getY() - foot.getY()) / 2;
			sMin = Math.min(sMin, s); sMax = Math.max(sMax, s);
			rMin = Math.min(rMin, r); rMax = Math.max(rMax, r);
		}
		if (leaves.size() != (sMax - sMin + 1) * (rMax - rMin + 1)) return null;

		// The hinge end: where the left hinge hangs while closed, and back toward the wall once open.
		Direction hingeEnd = open ? facing.getOpposite()
			: hinge == DoorHingeSide.LEFT ? lowEnd(facing) : lowEnd(facing).getOpposite();
		boolean hingeOuter = !leaves.contains(foot.relative(hingeEnd));
		boolean swingOuter = !leaves.contains(foot.relative(hingeEnd.getOpposite()));

		// The template's low-z end is the hinge on an unflipped model and the swinging edge on a
		// flipped one; vanilla flips the sheet for a right hinge and again for an open leaf.
		boolean flip = (hinge == DoorHingeSide.RIGHT) != open;
		boolean low = flip ? swingOuter : hingeOuter;
		boolean high = flip ? hingeOuter : swingOuter;
		boolean bottomRow = !leaves.contains(foot.below(2));
		boolean topRow = !leaves.contains(foot.above(2));

		StringBuilder flags = new StringBuilder();
		if (low) flags.append('l');
		if (high) flags.append('r');
		if (half == DoubleBlockHalf.UPPER && topRow) flags.append('t');
		if (half == DoubleBlockHalf.LOWER && bottomRow) flags.append('b');
		if (half == DoubleBlockHalf.LOWER && bottomRow && swingOuter && !sliding) flags.append('h');
		if (flags.isEmpty()) flags.append('x');

		ExtraModelKey<BlockStateModel> key = KEYS.get(new Key(state.getBlock(), half, hinge, open, flags.toString(), facing));
		return key == null ? null : models.get(key);
	}

	/** Every leaf joined to this one: same door, same facing, same hinge, same state, hung or sliding alike, in a row or stacked. */
	private static Set<BlockPos> bank(BlockAndTintGetter level, BlockPos foot, BlockState like, Direction along, boolean sliding) {
		Set<BlockPos> found = new HashSet<>();
		Deque<BlockPos> pending = new ArrayDeque<>();
		found.add(foot);
		pending.add(foot);

		while (!pending.isEmpty() && found.size() < MAX_LEAVES) {
			BlockPos at = pending.poll();
			for (BlockPos next : new BlockPos[] {at.relative(along), at.relative(along.getOpposite()),
					at.above(2), at.below(2)}) {
				if (found.contains(next) || !joins(level.getBlockState(next), like)) continue;
				if (ClientBlockMarks.has(next, DETACHED) || ClientBlockMarks.has(next, SLIDING) != sliding) continue;
				found.add(next);
				pending.add(next);
			}
		}
		return found;
	}

	private static boolean joins(BlockState state, BlockState like) {
		return state.is(like.getBlock())
			&& state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
			&& state.getValue(DoorBlock.FACING) == like.getValue(DoorBlock.FACING)
			&& state.getValue(DoorBlock.HINGE) == like.getValue(DoorBlock.HINGE)
			&& state.getValue(DoorBlock.OPEN) == like.getValue(DoorBlock.OPEN);
	}

	/**
	 * A door by what it carries, not by its class: the client's copy of a synced door is built
	 * from its base block's properties and is not a DoorBlock, but it has a door's half, hinge,
	 * facing and open state, which is all that is asked of it here.
	 */
	static boolean isDoor(BlockState state) {
		return state.hasProperty(DoorBlock.HALF) && state.hasProperty(DoorBlock.HINGE)
			&& state.hasProperty(DoorBlock.FACING) && state.hasProperty(DoorBlock.OPEN);
	}
}
