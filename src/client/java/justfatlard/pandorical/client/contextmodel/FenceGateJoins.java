package justfatlard.pandorical.client.contextmodel;

import justfatlard.pandorical.BlockMarkLookup;
import justfatlard.pandorical.api.BlockMarkApi;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * Gates side by side drawn as one wide gate, from
 * {@code <gate>[_wall][_open]_join_<left|right|both|none>[_stacked]_<facing>}. The facing is in
 * the name because a model picked here gets no blockstate rotation. Left and right are the gate's
 * own, looking the way it faces.
 */
@Environment(EnvType.CLIENT)
public final class FenceGateJoins implements ContextModels.Provider {
	private record Key(Block block, boolean open, boolean wall, String join, String swing, boolean stacked, Direction facing) {}

	/**
	 * A lone gate marked {@link BlockMarkApi#GATE_HINGE_LEFT} or
	 * {@link BlockMarkApi#GATE_HINGE_RIGHT} opens as
	 * {@code <gate>[_wall]_open_swing_<left|right>[_stacked]_<facing>}.
	 */
	private static final String[] SWINGS = {"left", "right"};

	private static final Map<Key, ExtraModelKey<BlockStateModel>> KEYS = new HashMap<>();
	private static final String[] JOINS = {"left", "right", "both", "none"};

	@Override
	public void scan(ResourceManager resources, ContextModels.Registrar add) {
		KEYS.clear();
		for (Block block : BuiltInRegistries.BLOCK) {
			if (!(block instanceof FenceGateBlock)) continue;
			Identifier id = BuiltInRegistries.BLOCK.getKey(block);
			scanSwings(resources, add, block, id);
			for (boolean wall : new boolean[] {false, true}) {
				for (boolean open : new boolean[] {false, true}) {
					for (String join : JOINS) {
						for (boolean stacked : new boolean[] {false, true}) {
							for (Direction facing : Direction.Plane.HORIZONTAL) {
								String path = id.getPath() + (wall ? "_wall" : "") + (open ? "_open" : "")
									+ "_join_" + join + (stacked ? "_stacked" : "") + "_" + facing.getSerializedName();
								Identifier file = Identifier.fromNamespaceAndPath(id.getNamespace(), "models/block/" + path + ".json");
								if (resources.getResource(file).isPresent()) {
									KEYS.put(new Key(block, open, wall, join, "", stacked, facing),
										add.add(Identifier.fromNamespaceAndPath(id.getNamespace(), "block/" + path)));
								}
							}
						}
					}
				}
			}
		}
	}

	private static void scanSwings(ResourceManager resources, ContextModels.Registrar add, Block block, Identifier id) {
		for (boolean wall : new boolean[] {false, true}) {
			for (String swing : SWINGS) {
				for (boolean stacked : new boolean[] {false, true}) {
					for (Direction facing : Direction.Plane.HORIZONTAL) {
						String path = id.getPath() + (wall ? "_wall" : "") + "_open_swing_" + swing
							+ (stacked ? "_stacked" : "") + "_" + facing.getSerializedName();
						Identifier file = Identifier.fromNamespaceAndPath(id.getNamespace(), "models/block/" + path + ".json");
						if (resources.getResource(file).isPresent()) {
							KEYS.put(new Key(block, true, wall, "none", swing, stacked, facing),
								add.add(Identifier.fromNamespaceAndPath(id.getNamespace(), "block/" + path)));
						}
					}
				}
			}
		}
	}

	@Override
	public BlockStateModel pick(BlockState state, BlockAndTintGetter level, BlockPos pos, ContextModels.Lookup models) {
		if (KEYS.isEmpty() || !(state.getBlock() instanceof FenceGateBlock)) return null;
		Direction facing = state.getValue(FenceGateBlock.FACING);
		boolean left = joined(state, level.getBlockState(pos.relative(facing.getCounterClockWise())));
		boolean right = joined(state, level.getBlockState(pos.relative(facing.getClockWise())));
		boolean stacked = joined(state, level.getBlockState(pos.below()));
		boolean open = state.getValue(FenceGateBlock.OPEN);
		String swing = "";
		if (open && !left && !right) {
			if (BlockMarkLookup.client.test(pos, BlockMarkApi.GATE_HINGE_LEFT)) swing = "left";
			else if (BlockMarkLookup.client.test(pos, BlockMarkApi.GATE_HINGE_RIGHT)) swing = "right";
		}
		if (!left && !right && !stacked && swing.isEmpty()) return null;
		String join = left && right ? "both" : left ? "left" : right ? "right" : "none";
		boolean wall = state.getValue(FenceGateBlock.IN_WALL);
		ExtraModelKey<BlockStateModel> key = KEYS.get(new Key(state.getBlock(), open, wall, join, swing, stacked, facing));
		if (key == null && !swing.isEmpty()) {
			key = KEYS.get(new Key(state.getBlock(), open, wall, join, "", stacked, facing));
		}
		return key == null ? null : models.get(key);
	}

	private static boolean joined(BlockState mine, BlockState other) {
		return other.getBlock() == mine.getBlock()
			&& other.getValue(FenceGateBlock.FACING).getAxis() == mine.getValue(FenceGateBlock.FACING).getAxis();
	}
}
