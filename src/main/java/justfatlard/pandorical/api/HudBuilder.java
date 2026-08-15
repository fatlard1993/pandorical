package justfatlard.pandorical.api;

import justfatlard.pandorical.protocol.ComponentDef;
import justfatlard.pandorical.protocol.ShowHudS2C;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fluent builder for constructing ShowHudS2C payloads.
 * Server mods use this to describe HUD overlays declaratively.
 */
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
	 * Screen anchor for this overlay. One of {@code "top_left"}, {@code "top_right"},
	 * {@code "bottom_left"}, {@code "bottom_right"} (offset is a margin from that corner),
	 * {@code "center"} (offset is a pixel nudge from true screen center; useful for prompts that
	 * need to sit near the crosshair, which no corner anchor can reach), or
	 * {@code "bottom_center"} (offsetX is the signed position of the overlay's LEFT edge relative
	 * to horizontal center, offsetY a margin up from the bottom edge; made for sitting with the
	 * vanilla hotbar/status rows, whose layout is center-relative). Defaults to "top_right".
	 * Clients predating an anchor value fall back to top-left placement.
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

	/** Add a map component. Props: map_id, rotate */
	public HudBuilder map(String id, int x, int y, int size, Map<String, String> props) {
		this.components.add(new ComponentBuilder(id, ComponentType.MAP)
			.bounds(x, y, size, size).props(props).build());
		return this;
	}

	/** Add a text component. */
	public HudBuilder text(String id, int x, int y, String text) {
		this.components.add(new ComponentBuilder(id, ComponentType.TEXT)
			.pos(x, y).prop("text", text).build());
		return this;
	}

	/** Add a sprite (colored rectangle). */
	public HudBuilder sprite(String id, int x, int y, int w, int h, Map<String, String> props) {
		this.components.add(new ComponentBuilder(id, ComponentType.SPRITE)
			.bounds(x, y, w, h).props(props).build());
		return this;
	}

	/**
	 * Add a particle burst: {@code count} particles orbiting the center of the given bounds at
	 * {@code radius} pixels, rotating at {@code speedDegPerSec} degrees/second. The client
	 * simulates the motion locally every frame; no per-particle server updates needed. Use
	 * {@code extraProps} for {@link ComponentType#PROP_PARTICLE_SIZE}, {@link ComponentType#PROP_COLOR},
	 * or {@link ComponentType#PROP_START_ANGLE}.
	 */
	public HudBuilder particleBurst(String id, int x, int y, int w, int h,
									 int count, float radius, float speedDegPerSec,
									 Map<String, String> extraProps) {
		Map<String, String> props = new java.util.HashMap<>(extraProps);
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
