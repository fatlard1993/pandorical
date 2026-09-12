package justfatlard.pandorical.client.rail;

import justfatlard.pandorical.api.BlockMarkApi;
import java.util.HashMap;
import java.util.Map;
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

/**
 * Fence gates side by side drawn as one wide gate.
 *
 * <p>A gate with the same gate on its left, its right, or both, on the same line, takes a
 * joined model: the post on the shared side gone, the bars running through. The models come
 * from whoever ships them, named {@code <gate>[_wall][_open]_join_<left|right|both>_<facing>}
 * beside the block's own models; the facing is in the name because a model picked here has
 * no blockstate rotation to lean on. Left and right are the gate's own, looking the way it
 * faces. A gate without joined models keeps its posts.
 */
@Environment(EnvType.CLIENT)
public final class FenceGateJoins implements ContextModels.Provider {
	private record Key(Block block, boolean open, boolean wall, String join, String swing, boolean stacked, Direction facing) {}

	/**
	 * The single leaves a gate marked {@link BlockMarkApi#GATE_HINGE_LEFT} or
	 * {@link BlockMarkApi#GATE_HINGE_RIGHT} opens as, drawn by
	 * {@code <gate>[_wall]_open_swing_<left|right>[_stacked]_<facing>}. A pair never swings as one
	 * leaf: a leaf two blocks long has no model to fit in.
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
		// A gate on a gate: the posts run down to meet the one below, so a tall gate has posts
		// the whole way rather than a gap at every storey.
		boolean stacked = joined(state, level.getBlockState(pos.below()));
		boolean open = state.getValue(FenceGateBlock.OPEN);
		String swing = "";
		if (open && !left && !right) {
			if (justfatlard.pandorical.BlockMarkLookup.client.test(pos, BlockMarkApi.GATE_HINGE_LEFT)) swing = "left";
			else if (justfatlard.pandorical.BlockMarkLookup.client.test(pos, BlockMarkApi.GATE_HINGE_RIGHT)) swing = "right";
		}
		if (!left && !right && !stacked && swing.isEmpty()) return null;
		String join = left && right ? "both" : left ? "left" : right ? "right" : "none";
		boolean wall = state.getValue(FenceGateBlock.IN_WALL);
		ExtraModelKey<BlockStateModel> key = KEYS.get(new Key(state.getBlock(), open, wall, join, swing, stacked, facing));
		if (key == null && !swing.isEmpty()) {
			// A gate without single-leaf models opens the way it always did.
			key = KEYS.get(new Key(state.getBlock(), open, wall, join, "", stacked, facing));
		}
		return key == null ? null : models.get(key);
	}

	/** The same gate on the same line, facing along it either way. */
	private static boolean joined(BlockState mine, BlockState other) {
		return other.getBlock() == mine.getBlock()
			&& other.getValue(FenceGateBlock.FACING).getAxis() == mine.getValue(FenceGateBlock.FACING).getAxis();
	}
}
