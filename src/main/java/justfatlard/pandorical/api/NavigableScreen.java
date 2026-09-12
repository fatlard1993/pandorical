package justfatlard.pandorical.api;

import java.util.List;

/**
 * A screen that says where its interactive parts are, so a gamepad or other directional navigator
 * can reach components that are not vanilla widgets. Regions are geometry only: a navigator moves
 * the pointer onto one and clicks through the screen's ordinary mouse path.
 */
public interface NavigableScreen {
	/**
	 * Every region a navigator may land on, in screen coordinates, in no particular order. Ask
	 * again on each step rather than caching: components move whenever the server says, and
	 * interpolate for several ticks after.
	 */
	List<NavRegion> navRegions();

	/** {@code id} is the id of the component the region came from. */
	record NavRegion(String id, int x, int y, int width, int height) {
		public int centerX() {
			return x + width / 2;
		}

		public int centerY() {
			return y + height / 2;
		}
	}
}
