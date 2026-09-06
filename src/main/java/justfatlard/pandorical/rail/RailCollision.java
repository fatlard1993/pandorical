package justfatlard.pandorical.rail;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Rails a player can stand on, when a server has asked for them.
 *
 * <p>Vanilla rails have no collision at all, which is fine while every rail sits on a block and
 * useless the moment a mod lets rails float: a bridge of rail is a bridge you fall through.
 * This gives a rail a floor - two pixels of deck for a flat one, a ramp of eight steps for a
 * sloped one - and gives it to players only. Carts, mobs and dropped items pass through rails
 * exactly as vanilla has them, so nothing about how a cart rides a rail changes.
 *
 * <p>The rule is the server's. A server-side mod switches it on through the content API; the
 * flag travels with the content sync, and the client applies the same shape, which is what
 * makes standing on a rail predict right. Off, this returns null everywhere and rails are
 * vanilla's.
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

	/** The floor this rail offers whoever is asking, or null when it offers none. */
	public static VoxelShape shapeFor(BlockState state, CollisionContext context) {
		if (!solid) return null;
		if (!(context instanceof EntityCollisionContext entity) || !(entity.getEntity() instanceof Player)) {
			return null;
		}
		RailShape shape = railShapeOf(state);
		return shape == null ? null : SHAPES.get(shape);
	}

	/**
	 * Whichever property of the state holds a rail shape; rails and their stand-ins name it
	 * differently.
	 *
	 * <p>A stand-in for a rail whose {@code shape} offers a different set of values from
	 * vanilla's - a crossing that is only ever straight, a junction with no slopes - is rebuilt on
	 * the client with a property of names rather than of {@link RailShape}, so the class test
	 * finds nothing there. Its names are still the shape names, so a property called
	 * {@code shape} is read by name too. Without that the server floored the rail and the client
	 * did not, and a player walking a crossing was put back up two pixels every tick.
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

	private static final Map<String, RailShape> BY_NAME = new java.util.HashMap<>();

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

	private static VoxelShape ramp(java.util.function.IntFunction<VoxelShape> step) {
		VoxelShape ramp = Shapes.empty();
		for (int i = 0; i < STEPS; i++) {
			ramp = Shapes.or(ramp, step.apply(i));
		}
		return ramp.optimize();
	}
}
