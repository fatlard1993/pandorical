package justfatlard.pandorical.api;

/**
 * Component type constants and property name constants.
 * Use these instead of raw strings to avoid typos.
 */
public final class ComponentType {
    private ComponentType() {}

    // --- Component types ---

    public static final String PANEL = "panel";
    public static final String BUTTON = "button";
    public static final String TEXT = "text";
    public static final String TEXT_INPUT = "text_input";
    public static final String ITEM_SLOT = "item_slot";
    public static final String ITEM_ICON = "item_icon";
    public static final String INVENTORY_GRID = "inventory_grid";
    public static final String SCROLL_PANEL = "scroll_panel";
    public static final String SPRITE = "sprite";
    public static final String MAP = "map";
    /** A small burst of particle-like sprites the client simulates locally (currently: orbit motion). */
    public static final String PARTICLE_BURST = "particle_burst";

    // --- Common prop keys ---

    /** Background color. Accepts #RRGGBB or #AARRGGBB. Used by: panel, scroll_panel, sprite */
    public static final String PROP_BACKGROUND = "background";
    /**
     * Full texture identifier including extension for a textured sprite, e.g.
     * {@code "mymod:textures/gui/my_icon.png"} (synced mod assets resolve through the virtual
     * resource pack). The texture is stretched to the component's bounds. When absent or
     * unresolvable the sprite falls back to its color fill, which is also what clients
     * predating this prop render. Used by: sprite
     */
    public static final String PROP_TEXTURE = "texture";
    /**
     * The texture's native pixel size for a textured sprite. When both are set, the texture is
     * drawn at native size and CLIPPED to the component's bounds instead of stretched: animating
     * the component's width then reveals or hides the texture from its left edge, which is how a
     * shaped progress fill works (e.g. a fill following a curved path). Clients predating these
     * props stretch instead of clipping. Used by: sprite
     */
    public static final String PROP_TEXTURE_WIDTH = "texture_width";
    public static final String PROP_TEXTURE_HEIGHT = "texture_height";
    /**
     * Top-left corner of the source region for a clipped sprite (requires
     * {@link #PROP_TEXTURE_WIDTH}/{@link #PROP_TEXTURE_HEIGHT}), in texture pixels. Clipping alone
     * always reveals from the top left; moving the source origin in step with the component's
     * position and size reveals from any edge instead, which is how a gauge that fills upward
     * works: push {@code y + (full - filled)}, {@code height = filled}, {@code texture_v = full -
     * filled} together. Clients predating these props draw from the texture's top left. Used by:
     * sprite
     */
    public static final String PROP_TEXTURE_U = "texture_u";
    public static final String PROP_TEXTURE_V = "texture_v";
    /** Text color. Accepts #RRGGBB or #AARRGGBB. Used by: text, button, particle_burst */
    public static final String PROP_COLOR = "color";

    // --- Geometry update keys ---
    // These are recognized directly by AbstractComponent.updateProps() and applied to the
    // component's live x/y/width/height fields (in addition to sitting in the generic prop map),
    // rather than requiring a separate typed field on the ComponentUpdate wire record. This keeps
    // the wire format unchanged (still Map<String,String>) and matches the existing convention of
    // encoding all numeric values as parseable strings (as color/int/float props already do).

    /** Absolute new X position (pixels). Recognized by every component type. */
    public static final String PROP_X = "x";
    /** Absolute new Y position (pixels). Recognized by every component type. */
    public static final String PROP_Y = "y";
    /** Absolute new width (pixels). Recognized by every component type. */
    public static final String PROP_WIDTH = "width";
    /** Absolute new height (pixels). Recognized by every component type. */
    public static final String PROP_HEIGHT = "height";
    /**
     * Uniform scale multiplier (1.0 = no scaling), applied around the component's center.
     * Parsed generically by every component type (needed so the shared render-time interpolation
     * transform in ScreenHelper always has a valid value), but only officially supported/documented
     * as a settable prop on: sprite, text.
     */
    /**
     * Ticks over which this component blends a changed value in. Defaults to a short
     * window that hides the gap between server updates; raise it when a change is
     * rare and meant to be watched rather than smoothed over.
     */
    public static final String PROP_INTERP_TICKS = "interp_ticks";
    public static final String PROP_SCALE = "scale";
    /**
     * Rotation in degrees, applied around the component's center.
     * Parsed generically by every component type (see {@link #PROP_SCALE}), but only officially
     * supported/documented as a settable prop on: sprite, text.
     */
    public static final String PROP_ROTATION = "rotation";

    // Panel props
    /** Border style: "beveled" (default) or "flat". */
    public static final String PROP_BORDER = "border";
    public static final String PROP_BORDER_LIGHT = "border_light";
    public static final String PROP_BORDER_DARK = "border_dark";
    public static final String PROP_BORDER_MID_LIGHT = "border_mid_light";
    public static final String PROP_BORDER_MID_DARK = "border_mid_dark";
    /** Flat border color. Only used when border="flat". */
    public static final String PROP_BORDER_COLOR = "border_color";

    // Button props
    /** Button label text. */
    public static final String PROP_LABEL = "label";
    /** Translatable key for button label. */
    public static final String PROP_LABEL_KEY = "label_key";
    /** "true"/"false": whether the button is clickable. */
    public static final String PROP_ENABLED = "enabled";
    /** Button style: "default" or "accepted" (green). */
    public static final String PROP_STYLE = "style";

    /** Button only: "#RRGGBB" bar down the leading edge, saying what kind of action this is. */
    public static final String PROP_ACCENT = "accent";

    // Text props
    public static final String PROP_TEXT = "text";
    /** Translatable key for display text. */
    public static final String PROP_TEXT_KEY = "text_key";
    /** "true"/"false": render text with shadow. */
    public static final String PROP_SHADOW = "shadow";

    /**
     * Horizontal alignment of {@link #TEXT} within its own width: {@code "left"}
     * (the default), {@code "center"} or {@code "right"}.
     *
     * <p>Alignment has to happen on the client because that is the only side that
     * can measure the text. A server sends a translation key and does not know
     * what language it will be read in, let alone how wide the result is.
     */
    public static final String PROP_ALIGN = "align";
    /** Max pixel width before wrapping. 0 = no wrap (default). */
    public static final String PROP_WRAP_WIDTH = "wrap_width";
    /** Max lines to display. 0 = unlimited (default). */
    public static final String PROP_MAX_LINES = "max_lines";

    // TextInput props
    /** Maximum character length for text input. */
    public static final String PROP_MAX_LENGTH = "max_length";
    /** Placeholder text shown when input is empty. */
    public static final String PROP_PLACEHOLDER = "placeholder";
    /** Translatable key for placeholder. */
    public static final String PROP_PLACEHOLDER_KEY = "placeholder_key";
    public static final String PROP_VALUE = "value";
    /** "true"/"false": whether the input accepts text. */
    public static final String PROP_EDITABLE = "editable";

    // ItemIcon props
    /** Registry ID of the item to display, e.g. "minecraft:red_shrub". */
    public static final String PROP_ITEM_ID = "item_id";
    /** Stack count to display in the decoration overlay (defaults to 1, hidden if 1). */
    public static final String PROP_ITEM_COUNT = "item_count";

    // ItemSlot props
    /** Slot index in the container. */
    public static final String PROP_SLOT_INDEX = "slot_index";
    /** "true"/"false": visual locked state. */
    public static final String PROP_LOCKED = "locked";
    /** Slot border style: "beveled" (default) or "flat". */
    public static final String PROP_SLOT_STYLE = "slot_style";

    // InventoryGrid props
    public static final String PROP_ROWS = "rows";
    public static final String PROP_COLS = "cols";
    /** Starting slot index. */
    public static final String PROP_START_SLOT = "start_slot";
    /** Slot index above which all slots are locked. */
    public static final String PROP_LOCKED_ABOVE = "locked_above";

    // ScrollPanel props
    /** Current scroll position in items. */
    public static final String PROP_SCROLL_OFFSET = "scroll_offset";
    /** Height per item in pixels. */
    public static final String PROP_ITEM_HEIGHT = "item_height";
    /** Number of visible items. */
    public static final String PROP_VISIBLE_ITEMS = "visible_items";
    /** Total item count for scroll bounds. */
    public static final String PROP_TOTAL_ITEMS = "total_items";
    /** "true"/"false": show scrollbar. */
    public static final String PROP_SHOW_SCROLLBAR = "show_scrollbar";

    // Map props
    /** Map ID (integer). The vanilla MapId to render. */
    public static final String PROP_MAP_ID = "map_id";
    /** "true"/"false": rotate map with player facing. Requires compass. */
    public static final String PROP_ROTATE = "rotate";

    // ParticleBurst props
    /** Number of particles in the burst. Default 8. */
    public static final String PROP_PARTICLE_COUNT = "particle_count";
    /** Pixel size of each individual particle square. Default 3. */
    public static final String PROP_PARTICLE_SIZE = "particle_size";
    /**
     * Motion pattern. Only {@code "orbit"} (the default) is implemented: particles are evenly
     * spaced around a circle centered on the component's bounds and rotate at {@link #PROP_SPEED}
     * degrees/second. Reserved for future patterns (e.g. burst-and-fade); an unrecognized value
     * currently falls back to orbit.
     */
    public static final String PROP_MOTION = "motion";
    /** Orbit radius in pixels. Defaults to half the component's shorter bound dimension. */
    public static final String PROP_RADIUS = "radius";
    /** Orbit angular speed in degrees/second. Negative values orbit the other direction. Default 90. */
    public static final String PROP_SPEED = "speed";
    /** Starting angle offset in degrees for the first particle. Default 0. */
    public static final String PROP_START_ANGLE = "start_angle";
}
