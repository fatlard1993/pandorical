package justfatlard.pandorical.client.rail;

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
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * A rectangle of trapdoors drawn as one trapdoor.
 *
 * <p>Trapdoors of one kind lying in one plane - flat at the same height, or standing open
 * against the same wall - and filling a rectangle are one hatch or one shutter: the frame goes
 * round the outside and the sheet's interior stretches across the inside. Each tile takes a
 * model named for the sides of the frame it keeps, {@code <trapdoor>_mega_<state>_<flags>},
 * which More Doors ships for every trapdoor in the game and every one of its own.
 */
@Environment(EnvType.CLIENT)
public final class TrapdoorBanks implements ContextModels.Provider {
	private record Key(Block block, String state, String flags, Direction facing) {}

	private static final Map<Key, ExtraModelKey<BlockStateModel>> KEYS = new HashMap<>();
	private static final int MAX_TILES = 64;
	private static final String[] FLAGS = {"x", "b", "t", "tb", "r", "rb", "rt", "rtb", "l", "lb", "lt", "ltb", "lr", "lrb", "lrt", "lrtb"};

	@Override
	public void scan(ResourceManager resources, ContextModels.Registrar add) {
		KEYS.clear();
		for (Block block : BuiltInRegistries.BLOCK) {
			if (!(block instanceof TrapDoorBlock)) continue;
			Identifier id = BuiltInRegistries.BLOCK.getKey(block);
			for (String state : new String[] {"bottom", "top", "open"}) {
				for (String flags : FLAGS) {
					String path = id.getPath() + "_mega_" + state + "_" + flags;
					Identifier file = Identifier.fromNamespaceAndPath(id.getNamespace(), "models/block/" + path + ".json");
					if (resources.getResource(file).isEmpty()) continue;
					Identifier model = Identifier.fromNamespaceAndPath(id.getNamespace(), "block/" + path);
					if (state.equals("open")) {
						// Vanilla's own turns for an open trapdoor: north as drawn, then a quarter turn per facing.
						for (Direction facing : Direction.Plane.HORIZONTAL) {
							KEYS.put(new Key(block, state, flags, facing),
								add.add(model, new Variant.SimpleModelState(Quadrant.R0, turn(facing), Quadrant.R0, false).asModelState()));
						}
					} else {
						KEYS.put(new Key(block, state, flags, Direction.NORTH), add.add(model, BlockModelRotation.IDENTITY));
					}
				}
			}
		}
	}

	private static Quadrant turn(Direction facing) {
		return switch (facing) {
			case EAST -> Quadrant.R90;
			case SOUTH -> Quadrant.R180;
			case WEST -> Quadrant.R270;
			default -> Quadrant.R0;
		};
	}

	@Override
	public BlockStateModel pick(BlockState state, BlockAndTintGetter level, BlockPos pos, ContextModels.Lookup models) {
		if (KEYS.isEmpty() || !(state.getBlock() instanceof TrapDoorBlock)) return null;
		boolean open = state.getValue(TrapDoorBlock.OPEN);
		Direction facing = state.getValue(TrapDoorBlock.FACING);

		// The sheet's axes in the world: u along, v across. Flat, that is east and south;
		// standing, it is along the wall and down from the top.
		Direction uDir = open ? modelWest(facing).getOpposite() : Direction.EAST;
		Direction vDir = open ? Direction.DOWN : Direction.SOUTH;

		Set<BlockPos> tiles = new HashSet<>();
		Deque<BlockPos> pending = new ArrayDeque<>();
		tiles.add(pos);
		pending.add(pos);
		while (!pending.isEmpty() && tiles.size() < MAX_TILES) {
			BlockPos at = pending.poll();
			for (BlockPos next : new BlockPos[] {at.relative(uDir), at.relative(uDir.getOpposite()),
					at.relative(vDir), at.relative(vDir.getOpposite())}) {
				if (tiles.contains(next) || !joins(level.getBlockState(next), state)) continue;
				tiles.add(next);
				pending.add(next);
			}
		}
		if (tiles.size() < 2) return null;

		int uMin = Integer.MAX_VALUE, uMax = Integer.MIN_VALUE, vMin = Integer.MAX_VALUE, vMax = Integer.MIN_VALUE;
		for (BlockPos tile : tiles) {
			int u = along(tile, pos, uDir), v = along(tile, pos, vDir);
			uMin = Math.min(uMin, u); uMax = Math.max(uMax, u);
			vMin = Math.min(vMin, v); vMax = Math.max(vMax, v);
		}
		if (tiles.size() != (uMax - uMin + 1) * (vMax - vMin + 1)) return null;

		StringBuilder flags = new StringBuilder();
		if (uMin == 0) flags.append('l');
		if (uMax == 0) flags.append('r');
		if (vMin == 0) flags.append('t');
		if (vMax == 0) flags.append('b');
		if (flags.isEmpty()) flags.append('x');

		String which = open ? "open" : state.getValue(TrapDoorBlock.HALF) == Half.TOP ? "top" : "bottom";
		ExtraModelKey<BlockStateModel> key = KEYS.get(new Key(state.getBlock(), which, flags.toString(),
			open ? facing : Direction.NORTH));
		return key == null ? null : models.get(key);
	}

	/** The world direction of the open template's west side, once turned for its facing. */
	private static Direction modelWest(Direction facing) {
		return switch (facing) {
			case EAST -> Direction.NORTH;
			case SOUTH -> Direction.EAST;
			case WEST -> Direction.SOUTH;
			default -> Direction.WEST;
		};
	}

	private static int along(BlockPos tile, BlockPos origin, Direction dir) {
		return (tile.getX() - origin.getX()) * dir.getStepX() + (tile.getY() - origin.getY()) * dir.getStepY()
			+ (tile.getZ() - origin.getZ()) * dir.getStepZ();
	}

	/** Same trapdoor, same state: lying at the same height, or standing open on the same wall. */
	private static boolean joins(BlockState other, BlockState like) {
		if (!other.is(like.getBlock()) || other.getValue(TrapDoorBlock.OPEN) != like.getValue(TrapDoorBlock.OPEN)) return false;
		if (like.getValue(TrapDoorBlock.OPEN)) return other.getValue(TrapDoorBlock.FACING) == like.getValue(TrapDoorBlock.FACING);
		return other.getValue(TrapDoorBlock.HALF) == like.getValue(TrapDoorBlock.HALF);
	}
}
