package justfatlard.pandorical.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * A scroll that is navigation, not a wheel gesture.
 *
 * <p>The wheel over a slot moves an item (see {@code ContainerHabitsMixin}), which is a fine
 * thing for a mouse to do and the wrong thing for a controller's shoulder buttons, whose
 * cursor sits on a slot because slots are what it is steered between. A pad driver raises this
 * around the scroll it sends, and the item habit stands aside so the list underneath scrolls.
 */
@Environment(EnvType.CLIENT)
public final class NavigationScroll {
	private NavigationScroll() {}

	private static boolean active;

	public static boolean isActive() {
		return active;
	}

	/** Run this as a navigation scroll. */
	public static void around(Runnable scroll) {
		active = true;
		try {
			scroll.run();
		} finally {
			active = false;
		}
	}
}
