package justfatlard.pandorical.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionfc;

/**
 * An entity turned any way at all, not only the yaw and pitch vanilla carries: a minecart upside
 * down under a ceiling, or standing on end up a wall.
 *
 * <p>The orientation is a turn of the entity's own frame, the frame vanilla draws it in at yaw 0
 * and pitch 0: its length along x, its top up y. It takes the place of yaw and pitch in the
 * drawing, about the point vanilla turns them about.
 *
 * <p>Its riders go with it. A rider is seated where an upright rider would sit, turned with the
 * entity about the entity's middle, and drawn turned the same way about their own middle; a rider
 * looking out of their own eyes has the view turned too, unless they have turned that off. The
 * seating holds on the server and on every client, so a rider under an upside-down cart is not
 * left with their head in the ceiling; the drawing and the view are for clients that
 * {@link #canOrient}. Everyone else sees the entity by its yaw and pitch alone.
 *
 * <p>Drawn turned in this version: minecarts. Other entities have their riders seated and viewed
 * turned, but are drawn by their yaw and pitch.
 *
 * <p>New in 15.40.
 */
public interface EntityOrientationApi {

	/**
	 * Turn it, and keep it turned until {@link #clear}. Cheap to call every tick: watching clients
	 * are told only when it has turned by more than a degree since they were last told, and they
	 * turn smoothly between what they are told.
	 */
	void orient(Entity entity, Quaternionfc orientation);

	/** Back to its yaw and pitch alone. */
	void clear(Entity entity);

	/** Whether this player's client draws an oriented entity turned. */
	boolean canOrient(ServerPlayer player);
}
