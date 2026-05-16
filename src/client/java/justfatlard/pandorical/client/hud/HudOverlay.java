package justfatlard.pandorical.client.hud;

import justfatlard.pandorical.client.component.PandoricalComponent;

import java.util.List;
import java.util.Map;

/**
 * Client-side state for a single HUD overlay sent by the server.
 */
public class HudOverlay {
	public final String overlayId;
	public final String anchor;
	public final int offsetX;
	public final int offsetY;
	public final List<PandoricalComponent> roots;
	public final Map<String, PandoricalComponent> componentIndex;

	public HudOverlay(String overlayId, String anchor, int offsetX, int offsetY,
					  List<PandoricalComponent> roots, Map<String, PandoricalComponent> componentIndex) {
		this.overlayId = overlayId;
		this.anchor = anchor;
		this.offsetX = offsetX;
		this.offsetY = offsetY;
		this.roots = roots;
		this.componentIndex = componentIndex;
	}

	/**
	 * Get the bounding box width of all root components.
	 */
	public int getWidth() {
		int minLeft = Integer.MAX_VALUE;
		int maxRight = 0;
		for (PandoricalComponent root : roots) {
			minLeft = Math.min(minLeft, root.getX());
			maxRight = Math.max(maxRight, root.getX() + root.getWidth());
		}
		return roots.isEmpty() ? 0 : maxRight - minLeft;
	}

	/**
	 * Get the bounding box height of all root components.
	 */
	public int getHeight() {
		int minTop = Integer.MAX_VALUE;
		int maxBottom = 0;
		for (PandoricalComponent root : roots) {
			minTop = Math.min(minTop, root.getY());
			maxBottom = Math.max(maxBottom, root.getY() + root.getHeight());
		}
		return roots.isEmpty() ? 0 : maxBottom - minTop;
	}
}
