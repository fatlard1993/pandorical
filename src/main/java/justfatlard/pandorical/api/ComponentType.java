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
    /**
     * What is moving about nearby, as dots on a disc that turns with the player: straight up is
     * the way they are looking, the centre is where they stand. Drawn every frame from the
     * client's own position and heading, so it keeps up with the camera; the server only says
     * who is there. Each blip names its entity, and the client follows that entity while it can
     * see it, falling back to the position that came with the blip when it cannot.
     *
     * <p>{@link #RADAR_BLIPS} is a list of {@code entityId,x,y,z,colour,size} separated by
     * semicolons: colour an ARGB integer, size 1 to 3. A seventh field of {@code p} marks a
     * player, drawn as a diamond rather than a square. {@link #RADAR_RANGE} is the disc's radius
     * in blocks. {@link #RADAR_TARGET_X} and {@link #RADAR_TARGET_Z} mark one place to head for,
     * drawn where it lies or on the rim when it is further than the range; leave them empty for
     * none.
     */
    public static final String RADAR = "radar";
    public static final String RADAR_BLIPS = "blips";
    public static final String RADAR_RANGE = "range";
    public static final String RADAR_TARGET_X = "target_x";
    public static final String RADAR_TARGET_Z = "target_z";
    /** A small burst of particle-like sprites the client simulates locally (currently: orbit motion). */
    public static final String PARTICLE_BURST = "particle_burst";
    /**
     * A sprite swept round its own centre by hand, within a sweep, and then pushed: the mouse's
     * sideways travel or a held A/D moves it, and the left button, space, W, up or enter holds
     * it where it is. It reports what it is doing - the angle as it moves, and the push going
     * down and coming up. A pick in a lock, a dial on a safe, anything worked by hand in real
     * time. The sweeping is the client's, so it never waits on the server; only the reports
     * cross the wire. The pointer is hidden while one is on screen, so a screen carrying a dial
     * should not also rely on anything that has to be clicked.
     *
     * <p>Its {@link #PROP_ROTATION} is the server's to set and is added to the hand's angle, so
     * a pick sitting in a cylinder turns with the cylinder when the cylinder is turned.
     */
    /**
     * A player's face, as the tab list draws it: the skin's face with its hat layer over it,
     * square, at the component's size. {@link #PROP_PLAYER} names whose. The client draws it
     * from the skin it already has for that player - the one on their body if they are in
     * sight, else the tab list's - so a skin override shows here as it does in the world, and
     * the server never has to know what anyone looks like. A client that has never heard of
     * the player draws the default skin that UUID would get.
     */
    public static final String PLAYER_FACE = "player_face";
    /** Player face: the player's UUID. */
    public static final String PROP_PLAYER = "player";
    public static final String DIAL = "dial";
    /** Dial: the full range it sweeps through, in degrees, centred on straight up. */
    public static final String PROP_SWEEP = "sweep";
    /**
     * Dial: how hard it is drawn trembling, nought to one, as a pick does against a jammed
     * cylinder - harder and faster the nearer it is to snapping. "true" and "false" are taken
     * as one and nought.
     */
    public static final String PROP_SHAKE = "shake";
    /** Dial: the key in every report saying which it is - "aim", "press" or "release". */
    public static final String DIAL_ACTION = "action";
    /** Dial: the key carrying the angle, in degrees, in every report. */
    public static final String DIAL_ANGLE = "angle";
    /**
     * A grid of palette-coloured cells painted by hand: press and drag to lay the current ink under
     * a square brush, right-click to ask for the colour under the pointer. The client paints at
     * once and reports what the hand did; the server applies the same report with
     * {@link PixelCanvas#apply} and acknowledges it, so nothing waits on a round trip and the two
     * copies stay the same. See {@link PixelCanvas} for the rule and the report's shape.
     *
     * <p>Cells are drawn square, as large as fit the bounds, centred in them.
     */
    public static final String PIXEL_CANVAS = "pixel_canvas";
    /** Pixel canvas: columns and rows. */
    public static final String PROP_CANVAS_COLUMNS = "columns";
    public static final String PROP_CANVAS_ROWS = "rows";
    /** Pixel canvas: comma-separated colours, #RRGGBB or #AARRGGBB, up to {@link PixelCanvas#MAX_PALETTE}; a cell is an index into it. */
    public static final String PROP_CANVAS_PALETTE = "palette";
    /** Pixel canvas: every cell, see {@link PixelCanvas#encode}. Authoritative: replaces the client's copy. */
    public static final String PROP_CANVAS_PIXELS = "pixels";
    /** Pixel canvas: the palette index a stroke lays down, or -1 for a canvas that cannot be painted. */
    public static final String PROP_CANVAS_INK = "ink";
    /** Pixel canvas: the brush's side, in cells. Default 1. */
    public static final String PROP_CANVAS_BRUSH = "brush";
    /** Pixel canvas: how much of each ink is left, see {@link PixelCanvas#encodeSupply}. Absent means unlimited. */
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

    /**
     * "true"/"false": whether the component is there at all. Recognized by every component type.
     *
     * <p>A hidden component is not drawn, takes no click, key or scroll, and a navigator cannot
     * land on it; its children go with it. This is how a screen swaps one set of controls for
     * another in place - a row of buttons for a search field, say - without reopening: build
     * both at open time, hide one, and flip the two on a press. Hidden is not the same as
     * {@link #PROP_ENABLED} false, which still draws the button and merely refuses the click.
     */
    public static final String PROP_VISIBLE = "visible";

    // --- Geometry update keys ---
    // These are recognized directly by AbstractComponent.updateProps() and applied to the
    // component's live x/y/width/height fields (in addition to sitting in the generic prop map),
    // rather than requiring a separate typed field on the ComponentUpdate wire record. This keeps
    // the wire format unchanged (still Map<String,String>) and matches the existing convention of
    // encoding all numeric values as parseable strings (as color/int/float props already do).

    /**
     * New X position (pixels), measured the way the component's own was when it was built: from its
     * parent's corner, or the screen's or overlay's for one at the top. Its children move with it.
     * Recognized by every component type.
     */
    public static final String PROP_X = "x";
    /** New Y position (pixels), measured as {@link #PROP_X} is. Recognized by every component type. */
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
    /**
     * GUI atlas sprite drawn centred on the button instead of a label, e.g.
     * {@code "mymod:icon_sort"} for {@code assets/mymod/textures/gui/sprites/icon_sort.png}.
     *
     * <p>For the small square buttons a row of controls is made of, where a word will not fit.
     * A font glyph is the obvious thing to reach for there and it is the wrong one: vanilla's
     * arrows and symbols are hairlines a single pixel wide, and a row of them beside vanilla's
     * own chunky widget art reads as a web page bolted to a game. Sprite art is drawn at the
     * weight the rest of the screen is.
     *
     * <p>Takes precedence over {@link #PROP_LABEL}; the sprite is drawn at its native size.
     */
    public static final String PROP_ICON = "icon";
    /** "true"/"false": whether the button is clickable. */
    public static final String PROP_ENABLED = "enabled";
    /** Button only: text shown beside the pointer while it rests on the button. What an icon cannot say. */
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
    /**
     * "true"/"false": give the field the keyboard, or take it away. A field a screen has just
     * revealed wants the first keystroke without a click to find it first; a field being hidden
     * must let go, or the keys keep landing in something nobody can see. Used by: text_input
     */
    public static final String PROP_FOCUSED = "focused";

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
    /**
     * Slot border style: "beveled" (default), "flat", or "none".
     *
     * <p>"none" draws no slot background at all, for a grid laid over a screen that already has
     * the slots in its own backdrop - a vanilla container texture used as the panel, say. The
     * grid still places the menu slots and vanilla still draws the items and the hover
     * highlight; only the frame under them is somebody else's to draw.
     */
    public static final String PROP_SLOT_STYLE = "slot_style";

    // InventoryGrid props
    public static final String PROP_ROWS = "rows";
    public static final String PROP_COLS = "cols";
    /** Starting slot index. */
    public static final String PROP_START_SLOT = "start_slot";
    /** Slot index above which all slots are locked. */
    public static final String PROP_LOCKED_ABOVE = "locked_above";
    /**
     * Comma-separated container slot indices to draw a dark veil over, on top of the item in
     * each, so everything else in the grid stands out. Empty string veils nothing.
     *
     * <p>The grid draws its frames under the items, which is why the lock overlay reads as a
     * shut slot; this one is drawn after the items, which is what a search result needs: the
     * item is still there and still hoverable, it has just stepped back. A server answering a
     * search names the slots that did NOT match. Used by: inventory_grid
     */
    public static final String PROP_DIM_SLOTS = "dim_slots";

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
