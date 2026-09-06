package justfatlard.pandorical.api;

/**
 * API for server mods to change how mounts handle.
 *
 * <p>Server-wide, and declared once at startup: these describe how riding works here, not how one
 * particular horse behaves. Both settings need the rider's client to agree, so Pandorical syncs
 * them - a mod turning one on without a Pandorical client would be overruled by that client every
 * tick.
 */
public interface MountApi {

	/** Let a horse carry a second passenger, sat behind the first. */
	void doubleRiders(boolean allow);

	/**
	 * Steer mounts like a boat instead of aiming them.
	 *
	 * <p>The mount keeps its own heading and turns with the strafe keys; the rider's view is their
	 * own. Being able to look somewhere other than where you are going is the whole point - it is
	 * what makes shooting from horseback possible.
	 */
	void freeLook(boolean enable);
}
