package justfatlard.pandorical.client.contextmodel;

import com.mojang.math.Quadrant;
import justfatlard.pandorical.BlockMarkLookup;
import justfatlard.pandorical.FenceGateLeaves;
import justfatlard.pandorical.api.BlockMarkApi;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fence gates drawn from their neighbours, from {@code <gate><suffix>} models facing south and
 * turned here by vanilla's own gate rotation. Left and right are the gate's own, looking the way
 * it faces. The names are written out in moredoor's generate_gate_models.py.
 *
 * <ul>
 *   <li>Shut, beside or on another gate: {@code [_wall]_join_<left|right|both>[_stacked]}, or
 *   {@code _join_none_stacked} when only stacked: one wide or tall frame.</li>
 *   <li>Open and alone: {@code [_wall]_open_swing_<left|right>[_stacked]} when marked
 *   {@link BlockMarkApi#GATE_HINGE_LEFT} or {@link BlockMarkApi#GATE_HINGE_RIGHT}, and
 *   {@code [_wall]_open_join_none_stacked} when only stacked.</li>
 *   <li>Open and wide ({@link BlockMarkApi#GATE_WIDE}): the gate's blocks stand out along the leaf,
 *   and each draws its own piece of it, {@code [_wall]_open_wide_<side>_<piece>[_stacked]}: the
 *   hinge square with more leaf in front is {@code hinge}, with none {@code hinge_tip}; a square
 *   swung out with more in front is {@code seg}, the last {@code tip}, as {@link FenceGateLeaves}
 *   reads it.</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public final class FenceGateJoins implements ContextModels.Provider {
	private record Key(Block block, String suffix, Direction facing) {}

	private static final Map<Key, ExtraModelKey<BlockStateModel>> KEYS = new HashMap<>();

	private static final String[] SIDES = {"left", "right"};
	private static final String[] PIECES = {"hinge", "hinge_tip", "seg", "tip"};

	/** Every suffix a gate may have a model for. */
	private static List<String> suffixes() {
		List<String> all = new ArrayList<>();
		for (String wall : new String[] {"", "_wall"}) {
			for (String join : new String[] {"left", "right", "both"}) {
				all.add(wall + "_join_" + join);
				all.add(wall + "_join_" + join + "_stacked");
			}
			all.add(wall + "_join_none_stacked");
			all.add(wall + "_open_join_none_stacked");
			for (String side : SIDES) {
				all.add(wall + "_open_swing_" + side);
				all.add(wall + "_open_swing_" + side + "_stacked");
				for (String piece : PIECES) {
					all.add(wall + "_open_wide_" + side + "_" + piece);
					all.add(wall + "_open_wide_" + side + "_" + piece + "_stacked");
				}
			}
		}
		return all;
	}

	@Override
	public void scan(ResourceManager resources, ContextModels.Registrar add) {
		KEYS.clear();
		List<String> suffixes = suffixes();
		for (Block block : BuiltInRegistries.BLOCK) {
			if (!(block instanceof FenceGateBlock)) continue;
			Identifier id = BuiltInRegistries.BLOCK.getKey(block);
			for (String suffix : suffixes) {
				String path = id.getPath() + suffix;
				Identifier file = Identifier.fromNamespaceAndPath(id.getNamespace(), "models/block/" + path + ".json");
				if (resources.getResource(file).isEmpty()) continue;
				Identifier model = Identifier.fromNamespaceAndPath(id.getNamespace(), "block/" + path);
				for (Direction facing : Direction.Plane.HORIZONTAL) {
					KEYS.put(new Key(block, suffix, facing),
						add.add(model, new Variant.SimpleModelState(Quadrant.R0, turn(facing), Quadrant.R0, false).asModelState()));
				}
			}
		}
	}

	/** Must match vanilla's fence gate blockstate rotations. */
	private static Quadrant turn(Direction facing) {
		return switch (facing) {
			case WEST -> Quadrant.R90;
			case NORTH -> Quadrant.R180;
			case EAST -> Quadrant.R270;
			default -> Quadrant.R0;
		};
	}

	@Override
	public BlockStateModel pick(BlockState state, BlockAndTintGetter level, BlockPos pos, ContextModels.Lookup models) {
		if (KEYS.isEmpty() || !(state.getBlock() instanceof FenceGateBlock)) return null;
		Direction facing = state.getValue(FenceGateBlock.FACING);
		String wall = state.getValue(FenceGateBlock.IN_WALL) ? "_wall" : "";
		boolean stacked = joined(state, level.getBlockState(pos.below()));
		String tall = stacked ? "_stacked" : "";
		boolean open = state.getValue(FenceGateBlock.OPEN);

		String suffix;
		String piece = FenceGateLeaves.piece(level, pos, state, BlockMarkLookup.client);
		if (piece != null) {
			suffix = wall + "_open_wide_" + FenceGateLeaves.side(BlockMarkLookup.client, pos) + "_" + piece + tall;
		} else if (open) {
			String side = FenceGateLeaves.side(BlockMarkLookup.client, pos);
			if (side != null) suffix = wall + "_open_swing_" + side + tall;
			else if (stacked) suffix = wall + "_open_join_none_stacked";
			else return null;
		} else {
			boolean left = joined(state, level.getBlockState(pos.relative(facing.getCounterClockWise())));
			boolean right = joined(state, level.getBlockState(pos.relative(facing.getClockWise())));
			if (!left && !right && !stacked) return null;
			String join = left && right ? "both" : left ? "left" : right ? "right" : "none";
			suffix = wall + "_join_" + join + tall;
		}
		ExtraModelKey<BlockStateModel> key = KEYS.get(new Key(state.getBlock(), suffix, facing));
		return key == null ? null : models.get(key);
	}

	private static boolean joined(BlockState mine, BlockState other) {
		return other.getBlock() == mine.getBlock()
			&& other.getValue(FenceGateBlock.FACING).getAxis() == mine.getValue(FenceGateBlock.FACING).getAxis();
	}
}
