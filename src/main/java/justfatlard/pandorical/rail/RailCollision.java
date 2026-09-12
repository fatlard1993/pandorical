package justfatlard.pandorical.rail;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * A floor on rails for players only, when a server asks for one; carts, mobs and items pass
 * through as in vanilla. The flag travels with the content sync so the client predicts the floor
 * the server enforces.
 */
public final class RailCollision {
	private RailCollision() {}

	private static volatile boolean solid;

	private static final int DECK = 2;
	private static final int STEPS = 8;
	private static final Map<RailShape, VoxelShape> SHAPES = new EnumMap<>(RailShape.class);

	static {
		for (RailShape shape : RailShape.values()) {
			SHAPES.put(shape, build(shape));
		}
	}

	public static void setSolid(boolean value) {
		solid = value;
	}

	public static boolean isSolid() {
		return solid;
	}

	/** Null leaves vanilla's shape. */
	public static VoxelShape shapeFor(BlockState state, CollisionContext context) {
		if (!solid) return null;
		if (!(context instanceof EntityCollisionContext entity) || !(entity.getEntity() instanceof Player)) {
			return null;
		}
		RailShape shape = railShapeOf(state);
		return shape == null ? null : SHAPES.get(shape);
	}

	/**
	 * A stand-in rail whose {@code shape} values differ from vanilla's is rebuilt on the client
	 * with a property of names, not of {@link RailShape}, so {@code shape} is also read by name.
	 * Otherwise the client and server floors disagree.
	 */
	private static RailShape railShapeOf(BlockState state) {
		for (Property<?> property : state.getProperties()) {
			if (property.getValueClass() == RailShape.class) {
				return (RailShape) state.getValue(property);
			}
		}
		for (Property<?> property : state.getProperties()) {
			if (!property.getName().equals("shape")) continue;
			RailShape named = BY_NAME.get(valueName(state, property));
			if (named != null) return named;
		}
		return null;
	}

	private static final Map<String, RailShape> BY_NAME = new HashMap<>();

	static {
		for (RailShape shape : RailShape.values()) BY_NAME.put(shape.getSerializedName(), shape);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static String valueName(BlockState state, Property property) {
		return property.getName((Comparable) state.getValue(property));
	}

	private static VoxelShape build(RailShape shape) {
		int rise = STEPS * DECK;
		return switch (shape) {
			case ASCENDING_EAST -> ramp(i -> Block.box(i * DECK, 0, 0, (i + 1) * DECK, (i + 1) * DECK, 16));
			case ASCENDING_WEST -> ramp(i -> Block.box(i * DECK, 0, 0, (i + 1) * DECK, rise - i * DECK, 16));
			case ASCENDING_SOUTH -> ramp(i -> Block.box(0, 0, i * DECK, 16, (i + 1) * DECK, (i + 1) * DECK));
			case ASCENDING_NORTH -> ramp(i -> Block.box(0, 0, i * DECK, 16, rise - i * DECK, (i + 1) * DECK));
			default -> Block.box(0, 0, 0, 16, DECK, 16);
		};
	}

	private static VoxelShape ramp(IntFunction<VoxelShape> step) {
		VoxelShape ramp = Shapes.empty();
		for (int i = 0; i < STEPS; i++) {
			ramp = Shapes.or(ramp, step.apply(i));
		}
		return ramp.optimize();
	}
}
