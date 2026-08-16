package justfatlard.pandorical.api;

import java.util.List;

/**
 * A screen that can say where its interactive parts are, so something other
 * than a mouse can reach them.
 *
 * <p>Vanilla screens are already discoverable this way: container screens
 * expose their slots through the menu, and widget screens expose theirs
 * through {@code Screen.children()}. Pandorical screens are not, because
 * their components are a private list built from server-sent
 * {@link justfatlard.pandorical.protocol.ComponentDef}s and are deliberately
 * not vanilla widgets. Without this, a gamepad or any other directional
 * navigator sees a Pandorical screen as empty and simply cannot press
 * anything on it.
 *
 * <p>Regions are geometry only, with no activate hook, and that is the point:
 * a navigator moves the pointer onto the region and clicks it through the
 * screen's ordinary mouse path. One mechanism drives vanilla slots, vanilla
 * widgets, and these alike, and none of them need to know a gamepad exists.
 */
public interface NavigableScreen {
	/**
	 * Every region a navigator may land on, in screen coordinates, in no
	 * particular order — callers pick by direction, not by index.
	 *
	 * <p>Called fresh on each navigation step rather than cached, since
	 * component geometry is mutable: the server can move or resize a
	 * component at any time, and {@code AbstractComponent} interpolates
	 * position over several ticks after it does.
	 */
	List<NavRegion> navRegions();

	/**
	 * One landable region. {@code id} is the component id it came from, kept
	 * for debugging and for navigators that want to remember where they were
	 * across a screen update.
	 */
	record NavRegion(String id, int x, int y, int width, int height) {
		public int centerX() {
			return x + width / 2;
		}

		public int centerY() {
			return y + height / 2;
		}
	}
}
