package justfatlard.pandorical.api;

import justfatlard.pandorical.protocol.ComponentDef;
import justfatlard.pandorical.protocol.ShowHudS2C;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HudBuilder {
	private final String overlayId;
	private String anchor = "top_right";
	private int offsetX = 5;
	private int offsetY = 5;
	private final List<ComponentDef> components = new ArrayList<>();

	public HudBuilder(String overlayId) {
		this.overlayId = overlayId;
	}

	/**
	 * Default {@code "top_right"}. {@code "top_left"}, {@code "top_right"}, {@code "bottom_left"}
	 * and {@code "bottom_right"} take the offset as a margin from that corner; {@code "center"} as
	 * a nudge from the screen's centre. {@code "bottom_center"} puts the overlay's left edge
	 * offsetX from the horizontal centre and offsetY up from the bottom, as the hotbar is laid
	 * out. {@code "top_center"} centres the overlay, nudges it by offsetX, drops it offsetY from
	 * the top, and hides it while the player list is open. A client that does not know an anchor
	 * places the overlay top-left.
	 */
	public HudBuilder anchor(String anchor) {
		this.anchor = anchor;
		return this;
	}

	public HudBuilder offset(int x, int y) {
		this.offsetX = x;
		this.offsetY = y;
		return this;
	}

	public HudBuilder component(ComponentDef component) {
		this.components.add(component);
		return this;
	}

	public HudBuilder component(ComponentBuilder builder) {
		this.components.add(builder.build());
		return this;
	}

	public HudBuilder map(String id, int x, int y, int size, Map<String, String> props) {
		this.components.add(new ComponentBuilder(id, ComponentType.MAP)
			.bounds(x, y, size, size).props(props).build());
		return this;
	}

	public HudBuilder text(String id, int x, int y, String text) {
		this.components.add(new ComponentBuilder(id, ComponentType.TEXT)
			.pos(x, y).prop("text", text).build());
		return this;
	}

	public HudBuilder sprite(String id, int x, int y, int w, int h, Map<String, String> props) {
		this.components.add(new ComponentBuilder(id, ComponentType.SPRITE)
			.bounds(x, y, w, h).props(props).build());
		return this;
	}

	/**
	 * Particles orbiting the centre of the bounds, animated by the client with no further
	 * updates. {@code extraProps} takes {@link ComponentType#PROP_PARTICLE_SIZE},
	 * {@link ComponentType#PROP_COLOR} and {@link ComponentType#PROP_START_ANGLE}.
	 */
	public HudBuilder particleBurst(String id, int x, int y, int w, int h,
									 int count, float radius, float speedDegPerSec,
									 Map<String, String> extraProps) {
		Map<String, String> props = new HashMap<>(extraProps);
		props.put(ComponentType.PROP_PARTICLE_COUNT, String.valueOf(count));
		props.put(ComponentType.PROP_RADIUS, String.valueOf(radius));
		props.put(ComponentType.PROP_SPEED, String.valueOf(speedDegPerSec));
		this.components.add(new ComponentBuilder(id, ComponentType.PARTICLE_BURST)
			.bounds(x, y, w, h).props(props).build());
		return this;
	}

	public String overlayId() {
		return overlayId;
	}

	public ShowHudS2C build() {
		return new ShowHudS2C(overlayId, anchor, offsetX, offsetY, List.copyOf(components));
	}
}
