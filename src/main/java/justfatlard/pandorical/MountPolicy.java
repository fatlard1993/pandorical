package justfatlard.pandorical;

/**
 * Mount rules, read by mixins on both sides: the rider's client moves the mount and tells the
 * server, so a rule applied on one side only is undone by the other.
 */
public final class MountPolicy {
	private MountPolicy() {}

	private static volatile boolean doubleRiders = false;
	private static volatile boolean freeLook = false;

	public static boolean doubleRiders() { return doubleRiders; }

	/** On, a mount keeps its own heading and turns with the strafe keys; the rider looks freely. */
	public static boolean freeLook() { return freeLook; }

	public static void set(boolean withDoubleRiders, boolean withFreeLook) {
		doubleRiders = withDoubleRiders;
		freeLook = withFreeLook;
	}

	public static void clear() {
		set(false, false);
	}
}
