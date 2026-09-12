package justfatlard.pandorical.api;

/**
 * Component types and prop keys.
 *
 * <p>{@code PROP_} keys are the ones a server sets on a component. Keys named for their type,
 * such as {@link #DIAL_ACTION}, are the ones a component's reports carry back to its handler.
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
    /**
     * Nearby entities as dots on a disc that turns with the player: up is where they look, the
     * centre is where they stand. The client draws it each frame from its own position and
     * heading, following each blip's entity while it can see it and falling back to the blip's
     * position when it cannot.
     *
     * <p>{@link #PROP_RADAR_BLIPS} is {@code entityId,x,y,z,colour,size} entries separated by
     * semicolons: colour an ARGB integer, size 1 to 3. A seventh field of {@code p} marks a
     * player, drawn as a diamond rather than a square. {@link #PROP_RADAR_RANGE} is the disc's
     * radius in blocks. {@link #PROP_RADAR_TARGET_X} and {@link #PROP_RADAR_TARGET_Z} mark one
     * place to head for, drawn on the rim when out of range; empty for none.
     */
    public static final String RADAR = "radar";
    public static final String PROP_RADAR_BLIPS = "blips";
    public static final String PROP_RADAR_RANGE = "range";
    public static final String PROP_RADAR_TARGET_X = "target_x";
    public static final String PROP_RADAR_TARGET_Z = "target_z";
    /** Particle-like sprites the client animates by itself. */
    public static final String PARTICLE_BURST = "particle_burst";
    /**
     * A sprite swept round its own centre by hand, within a sweep, and pushed: the mouse's
     * sideways travel or held A/D turns it, and the left button, space, W, up or enter pushes it,
     * holding it where it is. The client does the sweeping and reports the angle as it moves and
     * the push going down and up. The pointer is hidden while a dial is on screen, so nothing else
     * on that screen can rely on being clicked.
     *
     * <p>Its {@link #PROP_ROTATION} is the server's to set and is added to the hand's angle.
     */
    public static final String DIAL = "dial";
    /** Dial: the full range it sweeps through, in degrees, centred on straight up. */
    public static final String PROP_SWEEP = "sweep";
    /** Dial: how hard it trembles, 0 to 1; "true" and "false" are read as 1 and 0. */
    public static final String PROP_SHAKE = "shake";
    /** Dial: the report key saying which it is: "aim", "press" or "release". */
    public static final String DIAL_ACTION = "action";
    /** Dial: the key carrying the angle, in degrees, in every report. */
    public static final String DIAL_ANGLE = "angle";
    /**
     * A player's face with its hat layer, square, as the tab list draws it. Drawn from the skin
     * the client already has for that player, so a skin override shows here too; a player the
     * client has never heard of gets that UUID's default skin.
     */
    public static final String PLAYER_FACE = "player_face";
    /** Player face: the player's UUID. */
    public static final String PROP_PLAYER = "player";
    /**
     * A grid of palette-coloured cells painted by hand: drag to lay the current ink under a square
     * brush, right-click to pick the colour under the pointer. See {@link PixelCanvas} for the
     * rule, the reports and the replies. Cells are drawn square, as large as fit, centred.
     */
    public static final String PIXEL_CANVAS = "pixel_canvas";
    /** Pixel canvas: columns and rows. */
    public static final String PROP_CANVAS_COLUMNS = "columns";
    public static final String PROP_CANVAS_ROWS = "rows";
    /** Pixel canvas: comma-separated #RRGGBB or #AARRGGBB, up to {@link PixelCanvas#MAX_PALETTE}. */
    public static final String PROP_CANVAS_PALETTE = "palette";
    /** Pixel canvas: all cells, per {@link PixelCanvas#encode}; replaces the client's copy. */
    public static final String PROP_CANVAS_PIXELS = "pixels";
    /** Pixel canvas: the palette index a stroke lays down, or -1 for a canvas that cannot be painted. */
    public static final String PROP_CANVAS_INK = "ink";
    /** Pixel canvas: the brush's side, in cells. Default 1. */
    public static final String PROP_CANVAS_BRUSH = "brush";
    /** Pixel canvas: ink left, per {@link PixelCanvas#encodeSupply}; absent means unlimited. */
    public static final String PROP_CANVAS_SUPPLY = "supply";
    /**
     * Pixel canvas: the last report the server has applied. The client folds every report up to
     * it into its copy and replays the rest on top; pixels or supply sent in the same update are
     * the state after it.
     */
    public static final String PROP_CANVAS_ACK = "ack";

    // --- Common prop keys ---

    /** Background color. Accepts #RRGGBB or #AARRGGBB. Used by: panel, scroll_panel, sprite */
    public static final String PROP_BACKGROUND = "background";
    /**
     * Full texture id including extension, e.g. {@code "mymod:textures/gui/my_icon.png"},
     * stretched to the bounds. Absent or unresolvable, the sprite draws its colour fill, as older
     * clients do. Used by: sprite
     */
    public static final String PROP_TEXTURE = "texture";
    /**
     * The texture's native pixel size. With both set the texture is drawn at native size and
     * clipped to the bounds, not stretched, so animating the width reveals it from the left edge.
     * Older clients stretch. Used by: sprite
     */
    public static final String PROP_TEXTURE_WIDTH = "texture_width";
    public static final String PROP_TEXTURE_HEIGHT = "texture_height";
    /**
     * Top-left of the source region of a clipped sprite, in texture pixels. Moved in step with
     * position and size it reveals from any edge: a gauge filling upward pushes
     * {@code y + (full - filled)}, {@code height = filled} and {@code texture_v = full - filled}
     * together. Older clients draw from the top left. Used by: sprite
     */
    public static final String PROP_TEXTURE_U = "texture_u";
    public static final String PROP_TEXTURE_V = "texture_v";
    /** Text color. Accepts #RRGGBB or #AARRGGBB. Used by: text, button, particle_burst */
    public static final String PROP_COLOR = "color";

    /**
     * "true"/"false", on every component type. Hidden, a component and its children are not
     * drawn, take no click, key or scroll, and cannot be navigated to; {@link #PROP_ENABLED}
     * false still draws the button.
     */
    public static final String PROP_VISIBLE = "visible";

    // --- Geometry: every type takes x, y, width, height; the client interpolates each change ---

    /**
     * Pixels, from the parent's corner, or the screen's or overlay's for a top-level component.
     * Children move with it.
     */
    public static final String PROP_X = "x";
    /** Measured as {@link #PROP_X} is. */
    public static final String PROP_Y = "y";
    public static final String PROP_WIDTH = "width";
    public static final String PROP_HEIGHT = "height";
    /**
     * Ticks over which a changed value blends in. The default hides the gap between server
     * updates; raise it for a rare change meant to be watched.
     */
    public static final String PROP_INTERP_TICKS = "interp_ticks";
    /** Uniform scale about the centre, 1.0 for none. Used by: sprite, text */
    public static final String PROP_SCALE = "scale";
    /** Degrees about the centre. Used by: sprite, text */
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
    public static final String PROP_LABEL = "label";
    /** Translatable key for button label. */
    public static final String PROP_LABEL_KEY = "label_key";
    /**
     * GUI atlas sprite drawn centred at native size instead of the label, e.g.
     * {@code "mymod:icon_sort"} for {@code assets/mymod/textures/gui/sprites/icon_sort.png}.
     * Takes precedence over {@link #PROP_LABEL}.
     */
    public static final String PROP_ICON = "icon";
    /** "true"/"false": whether the button is clickable. */
    public static final String PROP_ENABLED = "enabled";
    /** Button only: text shown beside the pointer while it rests on the button. */
    public static final String PROP_TOOLTIP = "tooltip";
    /** Translatable key for {@link #PROP_TOOLTIP}; takes precedence over it. */
    public static final String PROP_TOOLTIP_KEY = "tooltip_key";
    /** Button style: "default", "accepted" (green), or "pressed" (sunken: the choice already made). */
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
     */
    public static final String PROP_ALIGN = "align";
    /** Max pixel width before wrapping. 0 = no wrap (default). */
    public static final String PROP_WRAP_WIDTH = "wrap_width";
    /** Max lines to display. 0 = unlimited (default). */
    public static final String PROP_MAX_LINES = "max_lines";

    // TextInput props
    public static final String PROP_MAX_LENGTH = "max_length";
    /** Placeholder text shown when input is empty. */
    public static final String PROP_PLACEHOLDER = "placeholder";
    /** Translatable key for placeholder. */
    public static final String PROP_PLACEHOLDER_KEY = "placeholder_key";
    public static final String PROP_VALUE = "value";
    /** "true"/"false": whether the input accepts text. */
    public static final String PROP_EDITABLE = "editable";
    /**
     * "true"/"false": give the field the keyboard, or take it away. Unfocus a field you hide, or
     * keystrokes keep landing in it. Used by: text_input
     */
    public static final String PROP_FOCUSED = "focused";

    // ItemIcon props
    /** Registry ID of the item to display, e.g. "minecraft:red_shrub". */
    public static final String PROP_ITEM_ID = "item_id";
    /** Stack count to display in the decoration overlay (defaults to 1, hidden if 1). */
    public static final String PROP_ITEM_COUNT = "item_count";

    // ItemSlot props
    public static final String PROP_SLOT_INDEX = "slot_index";
    /** "true"/"false": visual locked state. */
    public static final String PROP_LOCKED = "locked";
    /**
     * Slot border style: "beveled" (default), "flat", or "none", which draws no slot background
     * but still places the slots, items and hover highlight.
     */
    public static final String PROP_SLOT_STYLE = "slot_style";

    // InventoryGrid props
    public static final String PROP_ROWS = "rows";
    public static final String PROP_COLS = "cols";
    public static final String PROP_START_SLOT = "start_slot";
    /** Slot index above which all slots are locked. */
    public static final String PROP_LOCKED_ABOVE = "locked_above";
    /**
     * Comma-separated container slot indices to veil, drawn over the items, which stay
     * hoverable. Empty string veils nothing. Used by: inventory_grid
     */
    public static final String PROP_DIM_SLOTS = "dim_slots";

    // ScrollPanel props
    /** Current scroll position in items. */
    public static final String PROP_SCROLL_OFFSET = "scroll_offset";
    /** Height per item in pixels. */
    public static final String PROP_ITEM_HEIGHT = "item_height";
    public static final String PROP_VISIBLE_ITEMS = "visible_items";
    public static final String PROP_TOTAL_ITEMS = "total_items";
    /** "true"/"false": show scrollbar. */
    public static final String PROP_SHOW_SCROLLBAR = "show_scrollbar";

    // Map props
    /** Map ID (integer). The vanilla MapId to render. */
    public static final String PROP_MAP_ID = "map_id";
    /** "true"/"false": rotate map with player facing. Requires compass. */
    public static final String PROP_ROTATE = "rotate";
    /** Magnification of the map's centre, 1.0 for the whole map. Default 1.0. */
    public static final String PROP_MAP_ZOOM = "zoom";
    /** "true"/"false": the facing and coordinates line under the map. Default true. */
    public static final String PROP_MAP_SHOW_COORDS = "show_coords";
    /** "true"/"false": hostile mob dots, the red ones. Default true. */
    public static final String PROP_MAP_SHOW_HOSTILE = "show_hostile";
    /** "true"/"false": passive and other mob dots, the green and orange ones. Default true. */
    public static final String PROP_MAP_SHOW_PASSIVE = "show_passive";

    // ParticleBurst props
    /** Number of particles in the burst. Default 8. */
    public static final String PROP_PARTICLE_COUNT = "particle_count";
    /** Pixel size of each individual particle square. Default 3. */
    public static final String PROP_PARTICLE_SIZE = "particle_size";
    /**
     * Only {@code "orbit"}, the default, exists, and any other value orbits: particles evenly
     * spaced round a circle centred on the bounds, turning at {@link #PROP_SPEED}.
     */
    public static final String PROP_MOTION = "motion";
    /** Orbit radius in pixels. Defaults to half the component's shorter bound dimension. */
    public static final String PROP_RADIUS = "radius";
    /** Orbit angular speed in degrees/second. Negative values orbit the other direction. Default 90. */
    public static final String PROP_SPEED = "speed";
    /** Starting angle offset in degrees for the first particle. Default 0. */
    public static final String PROP_START_ANGLE = "start_angle";
}
