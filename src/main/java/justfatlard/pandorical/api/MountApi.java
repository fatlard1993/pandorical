package justfatlard.pandorical.api;

/**
 * Server-wide riding rules, declared once at mod init.
 *
 * <p>Both need the rider's client to agree, so Pandorical syncs them to each Pandorical client as
 * it arrives; a client without Pandorical overrules them every tick.
 */
public interface MountApi {

	/** Let a horse carry a second passenger, sat behind the first. */
	void doubleRiders(boolean allow);

	/**
	 * Steer mounts like a boat: the mount keeps its own heading and turns with the strafe keys,
	 * and the rider looks where they like.
	 */
	void freeLook(boolean enable);
}
