package justfatlard.pandorical.client.contextmodel;

import com.mojang.math.Quadrant;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws a run of alternating curves as a straight diagonal. A curve joined on either side by its
 * own bend turned half round draws the chord between its connected faces; a lone curve between
 * straights keeps its bend, and so do two curves making a U-turn. Where a run meets a straight they share one bend: the straight draws its half, and the
 * run's last chord draws the rest.
 *
 * <p>Models, beside the block's own, with {@code _on} before {@code _diagonal} when powered:
 * {@code <block>_diagonal_<se|sw|nw|ne>} is the chord; a trailing {@code _<n|e|s|w>} is the last
 * chord eased toward a straight beyond that face; {@code <block>_diagonal_end_<ne|nw>} is the
 * straight's half, modelled for a diagonal arriving through its south face and turned for the
 * others. A rail without them keeps its bends. Any block whose {@code shape} property is a
 * {@link RailShape} counts.
 */
@Environment(EnvType.CLIENT)
public final class RailDiagonals implements ContextModels.Provider {

	private record Key(Block block, boolean powered, RailShape curve) {}

	private record EaseKey(Block block, boolean powered, String hand, Direction through) {}

	private record EndKey(Block block, boolean powered, RailShape curve, Direction open) {}

	private static final Map<Key, ExtraModelKey<BlockStateModel>> KEYS = new HashMap<>();
	private static final Map<EaseKey, ExtraModelKey<BlockStateModel>> EASINGS = new HashMap<>();
	private static final Map<EndKey, ExtraModelKey<BlockStateModel>> ENDS = new HashMap<>();

	/** The quarter turn that carries the south-face end model to each face. */
	private static final Map<Direction, Quadrant> TURNS = Map.of(
		Direction.SOUTH, Quadrant.R0, Direction.WEST, Quadrant.R90, Direction.NORTH, Quadrant.R180, Direction.EAST, Quadrant.R270);
	private static final Map<Block, Property<RailShape>> SHAPE_PROPERTIES = new HashMap<>();

	private static final Map<RailShape, String> SUFFIX = Map.of(
		RailShape.SOUTH_EAST, "se", RailShape.SOUTH_WEST, "sw", RailShape.NORTH_WEST, "nw", RailShape.NORTH_EAST, "ne");

	@Override
	public void scan(ResourceManager resources, ContextModels.Registrar add) {
		KEYS.clear();
		EASINGS.clear();
		ENDS.clear();
		for (Block block : BuiltInRegistries.BLOCK) {
			if (shapeProperty(block) == null) continue;
			Identifier id = BuiltInRegistries.BLOCK.getKey(block);
			boolean hasPower = block.getStateDefinition().getProperties().contains(BlockStateProperties.POWERED);
			for (boolean powered : hasPower ? new boolean[] {false, true} : new boolean[] {false}) {
				for (var entry : SUFFIX.entrySet()) {
					String path = id.getPath() + (powered ? "_on" : "") + "_diagonal_" + entry.getValue();
					Identifier file = Identifier.fromNamespaceAndPath(id.getNamespace(), "models/block/" + path + ".json");
					if (resources.getResource(file).isPresent()) {
						KEYS.put(new Key(block, powered, entry.getKey()),
							add.add(Identifier.fromNamespaceAndPath(id.getNamespace(), "block/" + path)));
					}
					for (Direction open : connected(entry.getKey())) {
						String end = path + "_" + open.getSerializedName().charAt(0);
						Identifier endFile = Identifier.fromNamespaceAndPath(id.getNamespace(), "models/block/" + end + ".json");
						if (resources.getResource(endFile).isPresent()) {
							ENDS.put(new EndKey(block, powered, entry.getKey(), open),
								add.add(Identifier.fromNamespaceAndPath(id.getNamespace(), "block/" + end)));
						}
					}
				}
				for (String hand : new String[] {"ne", "nw"}) {
					String path = id.getPath() + (powered ? "_on" : "") + "_diagonal_end_" + hand;
					Identifier file = Identifier.fromNamespaceAndPath(id.getNamespace(), "models/block/" + path + ".json");
					if (!resources.getResource(file).isPresent()) continue;
					Identifier model = Identifier.fromNamespaceAndPath(id.getNamespace(), "block/" + path);
					for (var turn : TURNS.entrySet()) {
						EASINGS.put(new EaseKey(block, powered, hand, turn.getKey()),
							add.add(model, Variant.SimpleModelState.DEFAULT.withY(turn.getValue()).asModelState()));
					}
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static Property<RailShape> shapeProperty(Block block) {
		return SHAPE_PROPERTIES.computeIfAbsent(block, b -> {
			for (Property<?> property : b.getStateDefinition().getProperties()) {
				if (property.getName().equals("shape") && property.getValueClass() == RailShape.class) {
					return (Property<RailShape>) property;
				}
			}
			return null;
		});
	}

	@Override
	public BlockStateModel pick(BlockState state, BlockAndTintGetter level, BlockPos pos, ContextModels.Lookup models) {
		if (KEYS.isEmpty()) return null;
		Property<RailShape> property = shapeProperty(state.getBlock());
		if (property == null) return null;
		RailShape shape = state.getValue(property);
		boolean powered = state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED);

		if (SUFFIX.containsKey(shape)) {
			Direction[] sides = connected(shape);
			boolean first = stepsOn(level, pos, shape, sides[0]);
			boolean second = stepsOn(level, pos, shape, sides[1]);
			if (!first && !second) return null;
			if (first != second) {
				Direction open = first ? sides[1] : sides[0];
				if (straightAlong(level.getBlockState(pos.relative(open)), open)) {
					ExtraModelKey<BlockStateModel> end = ENDS.get(new EndKey(state.getBlock(), powered, shape, open));
					if (end != null) return models.get(end);
				}
			}
			ExtraModelKey<BlockStateModel> key = KEYS.get(new Key(state.getBlock(), powered, shape));
			return key == null ? null : models.get(key);
		}

		if (shape != RailShape.NORTH_SOUTH && shape != RailShape.EAST_WEST) return null;
		Direction[] ends = shape == RailShape.NORTH_SOUTH
			? new Direction[] {Direction.SOUTH, Direction.NORTH}
			: new Direction[] {Direction.WEST, Direction.EAST};
		for (Direction through : ends) {
			BlockPos beyond = pos.relative(through);
			BlockState neighbour = level.getBlockState(beyond);
			Property<RailShape> theirs = shapeProperty(neighbour.getBlock());
			if (theirs == null) continue;
			RailShape curve = neighbour.getValue(theirs);
			if (!SUFFIX.containsKey(curve) || !joins(curve, through.getOpposite())) continue;
			if (!stepped(level, beyond, curve)) continue;
			// The curve's other side gives the hand: clockwise of the entry face is "ne" on the
			// south-face model, and turns with it.
			Direction other = connected(curve)[0] == through.getOpposite() ? connected(curve)[1] : connected(curve)[0];
			String hand = other == through.getClockWise() ? "ne" : "nw";
			ExtraModelKey<BlockStateModel> key = EASINGS.get(new EaseKey(state.getBlock(), powered, hand, through));
			if (key != null) return models.get(key);
		}
		return null;
	}

	private static boolean stepped(BlockAndTintGetter level, BlockPos pos, RailShape shape) {
		for (Direction side : connected(shape)) {
			if (stepsOn(level, pos, shape, side)) return true;
		}
		return false;
	}

	/**
	 * Whether the rail beyond {@code side} is the next step of a diagonal: the same bend turned
	 * half round, which joins this one and carries on the way it was going. A curve that joins
	 * and bends back the way this one came is a U-turn, and both keep their bends.
	 */
	private static boolean stepsOn(BlockAndTintGetter level, BlockPos pos, RailShape curve, Direction side) {
		BlockState neighbour = level.getBlockState(pos.relative(side));
		Property<RailShape> theirs = shapeProperty(neighbour.getBlock());
		return theirs != null && neighbour.getValue(theirs) == turnedHalfRound(curve);
	}

	private static RailShape turnedHalfRound(RailShape curve) {
		return switch (curve) {
			case SOUTH_EAST -> RailShape.NORTH_WEST;
			case SOUTH_WEST -> RailShape.NORTH_EAST;
			case NORTH_WEST -> RailShape.SOUTH_EAST;
			case NORTH_EAST -> RailShape.SOUTH_WEST;
			default -> null;
		};
	}

	private static boolean straightAlong(BlockState neighbour, Direction through) {
		Property<RailShape> theirs = shapeProperty(neighbour.getBlock());
		if (theirs == null) return false;
		RailShape straight = through.getAxis() == Direction.Axis.Z ? RailShape.NORTH_SOUTH : RailShape.EAST_WEST;
		return neighbour.getValue(theirs) == straight;
	}

	private static Direction[] connected(RailShape curve) {
		return switch (curve) {
			case SOUTH_EAST -> new Direction[] {Direction.SOUTH, Direction.EAST};
			case SOUTH_WEST -> new Direction[] {Direction.SOUTH, Direction.WEST};
			case NORTH_WEST -> new Direction[] {Direction.NORTH, Direction.WEST};
			case NORTH_EAST -> new Direction[] {Direction.NORTH, Direction.EAST};
			default -> new Direction[0];
		};
	}

	private static boolean joins(RailShape curve, Direction side) {
		for (Direction joined : connected(curve)) {
			if (joined == side) return true;
		}
		return false;
	}
}
