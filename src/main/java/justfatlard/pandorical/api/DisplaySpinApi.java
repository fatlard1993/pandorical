package justfatlard.pandorical.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import org.joml.Vector3fc;

/**
 * A display entity turning by itself on the client: declared once, not a transformation sent every
 * tick. The display is drawn as its own transformation and then turned about {@code pivot} round
 * {@code axis}, both in the display's frame after that transformation: a block display's block spans
 * 0 to 1 there, an item display's item is centred on 0. The transformation itself can still glide
 * between poses as usual, and the turn rides on it, so a wheel keeps turning on a car that drives.
 *
 * <p>Only a client that can take {@link #canSpin} turns; to everyone else the display is drawn as its
 * transformation alone. A player who comes into view part way through sees it as far round as it has got.
 */
public interface DisplaySpinApi {

	/**
	 * Turn it at {@code degreesPerTick}, for {@code ticks} ticks and then held at the angle reached,
	 * or until {@link #stop} when {@code ticks} is 0. A turn ending on a whole number of turns ends
	 * looking as it started. Replaces any turn it had.
	 */
	void spin(Display display, Vector3fc pivot, Vector3fc axis, float degreesPerTick, int ticks);

	/** Drawn as its transformation alone again, wherever the turn had got to. */
	void stop(Display display);

	/** Whether this player's client turns a display told to spin. */
	boolean canSpin(ServerPlayer player);
}
