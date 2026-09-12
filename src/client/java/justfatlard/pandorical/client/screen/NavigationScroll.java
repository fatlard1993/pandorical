package justfatlard.pandorical.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * A pad driver sends its scroll through {@link #around}, so the wheel-over-slot item move in
 * {@code ContainerHabitsMixin} stands aside and the list underneath scrolls.
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
