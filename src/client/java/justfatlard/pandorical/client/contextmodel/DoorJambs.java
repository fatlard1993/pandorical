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
 * A door with a jamb where a fence meets it.
 *
 * <p>A fence arm stops at its block's edge aimed at the middle of the next; a door's panel
 * hugs the far edge of its own. A door with a fence connecting on its left, its right, or
 * both takes a model with a post where the arm arrives and rails across to the panel. Each
 * half looks beside itself, so the jamb stands as tall as the fence does. Named
 * {@code <door>_<lower|upper>_<hinge>[_open]_jamb_<left|right|both>_<facing>}, facing baked in.
 * More Doors ships them for the wooden doors and iron.
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
		// Each half asks about its own neighbours: a fence one high jambs the lower half alone,
		// and only a fence stacked beside the upper half carries the post up.
		boolean left = fenceReaches(level, pos, facing.getCounterClockWise());
		boolean right = fenceReaches(level, pos, facing.getClockWise());
		if (!left && !right) return null;
		String side = left && right ? "both" : (left ? "left" : "right");
		ExtraModelKey<BlockStateModel> key = KEYS.get(new Key(state.getBlock(), half,
			state.getValue(DoorBlock.HINGE), state.getValue(DoorBlock.OPEN), side, facing));
		return key == null ? null : models.get(key);
	}

	/** A fence on that side whose arm points back at the door. */
	private static boolean fenceReaches(BlockAndTintGetter level, BlockPos door, Direction side) {
		BlockState there = level.getBlockState(door.relative(side));
		if (!(there.getBlock() instanceof FenceBlock)) return false;
		var toward = PipeBlock.PROPERTY_BY_DIRECTION.get(side.getOpposite());
		return there.hasProperty(toward) && there.getValue(toward);
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
