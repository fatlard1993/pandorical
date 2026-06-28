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

    // --- Common prop keys ---

    /** Background color. Accepts #RRGGBB or #AARRGGBB. Used by: panel, scroll_panel, sprite */
    public static final String PROP_BACKGROUND = "background";
    /** Text color. Accepts #RRGGBB or #AARRGGBB. Used by: text, button */
    public static final String PROP_COLOR = "color";

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
    /** "true"/"false" — whether the button is clickable. */
    public static final String PROP_ENABLED = "enabled";
    /** Button style: "default" or "accepted" (green). */
    public static final String PROP_STYLE = "style";

    // Text props
    /** Display text. */
    public static final String PROP_TEXT = "text";
    /** Translatable key for display text. */
    public static final String PROP_TEXT_KEY = "text_key";
    /** "true"/"false" — render text with shadow. */
    public static final String PROP_SHADOW = "shadow";
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
    /** Current input value. */
    public static final String PROP_VALUE = "value";
    /** "true"/"false" — whether the input accepts text. */
    public static final String PROP_EDITABLE = "editable";

    // ItemIcon props
    /** Registry ID of the item to display, e.g. "minecraft:red_shrub". */
    public static final String PROP_ITEM_ID = "item_id";
    /** Stack count to display in the decoration overlay (defaults to 1, hidden if 1). */
    public static final String PROP_ITEM_COUNT = "item_count";

    // ItemSlot props
    /** Slot index in the container. */
    public static final String PROP_SLOT_INDEX = "slot_index";
    /** "true"/"false" — visual locked state. */
    public static final String PROP_LOCKED = "locked";
    /** Slot border style: "beveled" (default) or "flat". */
    public static final String PROP_SLOT_STYLE = "slot_style";

    // InventoryGrid props
    /** Number of rows in the grid. */
    public static final String PROP_ROWS = "rows";
    /** Number of columns in the grid. */
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
    /** "true"/"false" — show scrollbar. */
    public static final String PROP_SHOW_SCROLLBAR = "show_scrollbar";

    // Map props
    /** Map ID (integer). The vanilla MapId to render. */
    public static final String PROP_MAP_ID = "map_id";
    /** "true"/"false" — rotate map with player facing. Requires compass. */
    public static final String PROP_ROTATE = "rotate";
}
