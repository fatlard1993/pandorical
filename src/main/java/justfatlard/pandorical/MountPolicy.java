package justfatlard.pandorical;

/**
 * How mounts behave, on whichever side is asking.
 *
 * <p>Read by mixins that run on the client and the server both, because riding is simulated in two
 * places at once: the riding player's client decides where its mount goes and tells the server, so
 * a rule applied on only one side is a rule the other side spends its time undoing.
 *
 * <p>Set directly on the server by {@code MountApi} and from the policy packet on the client, which
 * is why it is a plain holder rather than living on either side's own class.
 */
public final class MountPolicy {
	private MountPolicy() {}

	private static volatile boolean doubleRiders = false;
	private static volatile boolean freeLook = false;

	/** Whether a horse will carry a second passenger. */
	public static boolean doubleRiders() { return doubleRiders; }

	/**
	 * Whether a mount is steered rather than aimed.
	 *
	 * <p>On, a horse holds its own heading and turns with the strafe keys, the way a boat does, and
	 * the rider's view is their own - so they can look behind them, or draw a bow at something off
	 * to the side, without the horse following their eyes into it.
	 */
	public static boolean freeLook() { return freeLook; }

	public static void set(boolean withDoubleRiders, boolean withFreeLook) {
		doubleRiders = withDoubleRiders;
		freeLook = withFreeLook;
	}

	/** A client that has left a server keeps none of its rules. */
	public static void clear() {
		set(false, false);
	}
}
