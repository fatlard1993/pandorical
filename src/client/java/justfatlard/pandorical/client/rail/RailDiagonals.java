package justfatlard.pandorical.client.rail;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import com.mojang.math.Quadrant;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;

/**
 * A run of alternating curved rails drawn as the straight diagonal it is.
 *
 * <p>A minecart cannot travel at forty-five degrees, so a diagonal line of track is a
 * staircase of curves, and vanilla draws every step of it as a bend: the classic S-wriggle.
 * The cart still wriggles; this only changes what is drawn. A curve with a curve joined to it
 * on either side is a step of a diagonal, and it is drawn with the chord between its two
 * connected faces instead of the bend. Chords meet end to end, so a run reads as one straight
 * line from the straight rail it leaves to the straight rail it reaches, and where one diagonal
 * turns into another the two lines meet at a sharp corner. The ends of the run are chords too:
 * a diagonal of three curves is three chords, not a chord between two bends, because the line
 * is what the player laid and the bends were only ever vanilla's way of not having one. A
 * curve on its own between two straights is a corner, and keeps its bend.
 *
 * <p>Where a diagonal meets a straight the two share one bend: an arc tangent to the straight
 * short of the shared face and to the chord the same distance past it, so both rails swing
 * round together the way rails do. The straight draws the part on its side, and the run's last
 * chord, the one with a straight beyond its other face, is drawn with the rest in place of its
 * plain end. The straight used to take the whole bend, and its outer rail swung out past the
 * block and back to reach the chord's.
 *
 * <p>The models come from whoever ships the rail: a model named
 * {@code <block>_diagonal_<se|sw|nw|ne>} beside the block's own models (with {@code _on}
 * before {@code _diagonal} for a powered state) is picked up at model load, and a rail without
 * one keeps its bend; {@code <block>_diagonal_<se|sw|nw|ne>_<n|e|s|w>} is the run's last chord
 * eased toward the straight beyond that face; {@code <block>_diagonal_end_<ne|nw>} is the
 * straight's half of the bend, drawn for a diagonal received through its south face and turned
 * for the other three. Any block with a
 * {@code shape} property of {@link RailShape} counts as a rail, which covers vanilla's and any
 * server-defined stand-in.
 *
 * <p>The swap happens where the chunk compiler looks a block's model up, because that is the
 * one place with the neighbours in hand: a model on its own no longer sees the world.
 */
@Environment(EnvType.CLIENT)
public final class RailDiagonals implements ContextModels.Provider {

	private record Key(Block block, boolean powered, RailShape curve) {}

	/** An easing: which hand the diagonal comes in on, and which face it comes through. */
	private record EaseKey(Block block, boolean powered, String hand, Direction through) {}

	/** A run's last chord: the curve, and the face with the straight beyond it. */
	private record EndKey(Block block, boolean powered, RailShape curve, Direction open) {}

	private static final Map<Key, ExtraModelKey<BlockStateModel>> KEYS = new HashMap<>();
	private static final Map<EaseKey, ExtraModelKey<BlockStateModel>> EASINGS = new HashMap<>();
	private static final Map<EndKey, ExtraModelKey<BlockStateModel>> ENDS = new HashMap<>();

	/** The face a diagonal comes through, and the quarter turn that carries the south-face model there. */
	private static final Map<Direction, Quadrant> TURNS = Map.of(
		Direction.SOUTH, Quadrant.R0, Direction.WEST, Quadrant.R90, Direction.NORTH, Quadrant.R180, Direction.EAST, Quadrant.R270);
	private static final Map<Block, Property<RailShape>> SHAPE_PROPERTIES = new HashMap<>();

	private static final Map<RailShape, String> SUFFIX = Map.of(
		RailShape.SOUTH_EAST, "se", RailShape.SOUTH_WEST, "sw", RailShape.NORTH_WEST, "nw", RailShape.NORTH_EAST, "ne");

	/** Every rail block in the registry, every chord model that actually exists for it. */
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

	/** The block's rail shape property, by name and value type, or null for anything that is not a rail. */
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

	/**
	 * The chord for a curve with a joining curve on either side, the easing for a straight with
	 * such a curve at one end, else nothing.
	 */
	@Override
	public BlockStateModel pick(BlockState state, BlockAndTintGetter level, BlockPos pos, ContextModels.Lookup models) {
		if (KEYS.isEmpty()) return null;
		Property<RailShape> property = shapeProperty(state.getBlock());
		if (property == null) return null;
		RailShape shape = state.getValue(property);
		boolean powered = state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED);

		if (SUFFIX.containsKey(shape)) {
			Direction[] sides = connected(shape);
			boolean first = joinedCurve(level, pos, sides[0]);
			boolean second = joinedCurve(level, pos, sides[1]);
			if (!first && !second) return null;
			if (first != second) {
				// The run's end: with a straight beyond the open face the two are eased together.
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
			// The curve's other side says which way the line runs: clockwise of the face it
			// comes through is the north-east hand on the south face, and turns with it.
			Direction other = connected(curve)[0] == through.getOpposite() ? connected(curve)[1] : connected(curve)[0];
			String hand = other == through.getClockWise() ? "ne" : "nw";
			ExtraModelKey<BlockStateModel> key = EASINGS.get(new EaseKey(state.getBlock(), powered, hand, through));
			if (key != null) return models.get(key);
		}
		return null;
	}

	/** Whether a curve here has a curve joined to it on either side: a step of a diagonal. */
	private static boolean stepped(BlockAndTintGetter level, BlockPos pos, RailShape shape) {
		for (Direction side : connected(shape)) {
			if (joinedCurve(level, pos, side)) return true;
		}
		return false;
	}

	/** Whether the block on that side is a curve joined back to this one. */
	private static boolean joinedCurve(BlockAndTintGetter level, BlockPos pos, Direction side) {
		BlockState neighbour = level.getBlockState(pos.relative(side));
		Property<RailShape> theirs = shapeProperty(neighbour.getBlock());
		if (theirs == null) return false;
		RailShape near = neighbour.getValue(theirs);
		return SUFFIX.containsKey(near) && joins(near, side.getOpposite());
	}

	/** Whether this is a flat straight running the way that face looks: one a chord can ease into. */
	private static boolean straightAlong(BlockState neighbour, Direction through) {
		Property<RailShape> theirs = shapeProperty(neighbour.getBlock());
		if (theirs == null) return false;
		RailShape straight = through.getAxis() == Direction.Axis.Z ? RailShape.NORTH_SOUTH : RailShape.EAST_WEST;
		return neighbour.getValue(theirs) == straight;
	}

	/** The two sides a curve joins. */
	private static Direction[] connected(RailShape curve) {
		return switch (curve) {
			case SOUTH_EAST -> new Direction[] {Direction.SOUTH, Direction.EAST};
			case SOUTH_WEST -> new Direction[] {Direction.SOUTH, Direction.WEST};
			case NORTH_WEST -> new Direction[] {Direction.NORTH, Direction.WEST};
			case NORTH_EAST -> new Direction[] {Direction.NORTH, Direction.EAST};
			default -> new Direction[0];
		};
	}

	/** Whether this curve has a connection on that side: a neighbour that turns its back is no step. */
	private static boolean joins(RailShape curve, Direction side) {
		for (Direction joined : connected(curve)) {
			if (joined == side) return true;
		}
		return false;
	}
}
