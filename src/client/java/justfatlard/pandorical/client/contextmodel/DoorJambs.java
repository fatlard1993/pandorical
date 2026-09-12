package justfatlard.pandorical.client.contextmodel;

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
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * A fence arm ends aimed at the middle of the door's block, but the panel hugs its far edge, so a
 * door with a fence arm reaching it takes
 * {@code <door>_<lower|upper>_<hinge>[_open]_jamb_<left|right|both>_<facing>}, facing baked in.
 */
@Environment(EnvType.CLIENT)
public final class DoorJambs implements ContextModels.Provider {
	private record Key(Block block, DoubleBlockHalf half, DoorHingeSide hinge, boolean open, String side, Direction facing) {}

	private static final Map<Key, ExtraModelKey<BlockStateModel>> KEYS = new HashMap<>();
	private static final String[] SIDES = {"left", "right", "both"};

	@Override
	public void scan(ResourceManager resources, ContextModels.Registrar add) {
		KEYS.clear();
		for (Block block : BuiltInRegistries.BLOCK) {
			if (!isDoor(block.defaultBlockState())) continue;
			Identifier id = BuiltInRegistries.BLOCK.getKey(block);
			for (DoubleBlockHalf half : DoubleBlockHalf.values()) {
				for (DoorHingeSide hinge : DoorHingeSide.values()) {
					for (boolean open : new boolean[] {false, true}) {
						for (String side : SIDES) {
							for (Direction facing : Direction.Plane.HORIZONTAL) {
								String path = id.getPath() + "_" + half.getSerializedName() + "_" + hinge.getSerializedName()
									+ (open ? "_open" : "") + "_jamb_" + side + "_" + facing.getSerializedName();
								Identifier file = Identifier.fromNamespaceAndPath(id.getNamespace(), "models/block/" + path + ".json");
								if (resources.getResource(file).isPresent()) {
									KEYS.put(new Key(block, half, hinge, open, side, facing),
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
		if (KEYS.isEmpty() || !isDoor(state)) return null;
		Direction facing = state.getValue(DoorBlock.FACING);
		DoubleBlockHalf half = state.getValue(DoorBlock.HALF);
		boolean left = fenceReaches(level, pos, facing.getCounterClockWise());
		boolean right = fenceReaches(level, pos, facing.getClockWise());
		if (!left && !right) return null;
		String side = left && right ? "both" : (left ? "left" : "right");
		ExtraModelKey<BlockStateModel> key = KEYS.get(new Key(state.getBlock(), half,
			state.getValue(DoorBlock.HINGE), state.getValue(DoorBlock.OPEN), side, facing));
		return key == null ? null : models.get(key);
	}

	private static boolean fenceReaches(BlockAndTintGetter level, BlockPos door, Direction side) {
		BlockState there = level.getBlockState(door.relative(side));
		if (!(there.getBlock() instanceof FenceBlock)) return false;
		var toward = PipeBlock.PROPERTY_BY_DIRECTION.get(side.getOpposite());
		return there.hasProperty(toward) && there.getValue(toward);
	}

	static boolean isDoor(BlockState state) {
		return state.hasProperty(DoorBlock.HALF) && state.hasProperty(DoorBlock.HINGE)
			&& state.hasProperty(DoorBlock.FACING) && state.hasProperty(DoorBlock.OPEN);
	}
}
