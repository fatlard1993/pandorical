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

	public String overlayId() {
		return overlayId;
	}

	public ShowHudS2C build() {
		return new ShowHudS2C(overlayId, anchor, offsetX, offsetY, List.copyOf(components));
	}
}
