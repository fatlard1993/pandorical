package justfatlard.pandorical.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * A pad driver sends its scroll through {@link #around}, marking it as navigation rather than a
 * wheel. Nothing in Pandorical treats the two differently any more; kept for the drivers that
 * call it.
 */
@Environment(EnvType.CLIENT)
public final class NavigationScroll {
	private NavigationScroll() {}

	private static boolean active;

	public static boolean isActive() {
		return active;
	}

	public static void around(Runnable scroll) {
		active = true;
		try {
			scroll.run();
		} finally {
			active = false;
		}
	}
}
