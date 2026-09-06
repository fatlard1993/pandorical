package justfatlard.pandorical.client.rail;

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
	private record Key(Block block, boolean open, boolean wall, String join, boolean stacked, Direction facing) {}

	private static final Map<Key, ExtraModelKey<BlockStateModel>> KEYS = new HashMap<>();
	private static final String[] JOINS = {"left", "right", "both", "none"};

	@Override
	public void scan(ResourceManager resources, ContextModels.Registrar add) {
		KEYS.clear();
		for (Block block : BuiltInRegistries.BLOCK) {
			if (!(block instanceof FenceGateBlock)) continue;
			Identifier id = BuiltInRegistries.BLOCK.getKey(block);
			for (boolean wall : new boolean[] {false, true}) {
				for (boolean open : new boolean[] {false, true}) {
					for (String join : JOINS) {
						for (boolean stacked : new boolean[] {false, true}) {
							for (Direction facing : Direction.Plane.HORIZONTAL) {
								String path = id.getPath() + (wall ? "_wall" : "") + (open ? "_open" : "")
									+ "_join_" + join + (stacked ? "_stacked" : "") + "_" + facing.getSerializedName();
								Identifier file = Identifier.fromNamespaceAndPath(id.getNamespace(), "models/block/" + path + ".json");
								if (resources.getResource(file).isPresent()) {
									KEYS.put(new Key(block, open, wall, join, stacked, facing),
										add.add(Identifier.fromNamespaceAndPath(id.getNamespace(), "block/" + path)));
								}
							}
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
		if (!left && !right && !stacked) return null;
		String join = left && right ? "both" : left ? "left" : right ? "right" : "none";
		ExtraModelKey<BlockStateModel> key = KEYS.get(new Key(state.getBlock(),
			state.getValue(FenceGateBlock.OPEN), state.getValue(FenceGateBlock.IN_WALL), join, stacked, facing));
		return key == null ? null : models.get(key);
	}

	/** The same gate on the same line, facing along it either way. */
	private static boolean joined(BlockState mine, BlockState other) {
		return other.getBlock() == mine.getBlock()
			&& other.getValue(FenceGateBlock.FACING).getAxis() == mine.getValue(FenceGateBlock.FACING).getAxis();
	}
}
