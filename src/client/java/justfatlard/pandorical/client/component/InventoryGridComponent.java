package justfatlard.pandorical.client.component;

import justfatlard.pandorical.protocol.ComponentDef;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Renders a grid of item slots and positions the menu's Slot objects to match.
 * This is the bridge between declarative UI and vanilla container slot sync.
 *
 * On init, repositions the corresponding Slot objects in PandoricalMenu so that
 * vanilla renders items at the correct positions.
 */
public class InventoryGridComponent extends AbstractComponent {
    private static final int MAX_GRID_SIDE = 64;

    /**
     * Vanilla's slot geometry: an eighteen pixel cell with a one pixel border, leaving exactly
     * the sixteen an item is drawn at.
     *
     * <p>The background used to be drawn sixteen wide inside an eighteen wide cell while the item
     * still went in at +1, so every item overhung its own box by a pixel to the bottom and right
     * and its stack count landed in the trough between cells. Two pixels of slack per cell, on
     * every grid Pandorical draws.
     */
    private static final int CELL_SIZE = 18;
    private static final int BORDER = 1;
    private static final int ITEM_SIZE = CELL_SIZE - BORDER * 2;
    private static final int SLOT_BORDER_DARK = 0xFF373737;
    private static final int SLOT_BORDER_LIGHT = 0xFFFFFFFF;
    private static final int SLOT_INNER = 0xFF8B8B8B;
    private static final int LOCKED_OVERLAY = 0xCC1A1A1A;
    /** Dark enough that the lit slots are the ones the eye lands on; the item still shows through. */
    private static final int DIM_OVERLAY = 0xA0000000;

    private int rows, cols, startSlot, lockedAbove;
    private String slotStyle;
    private Set<Integer> dimSlots = Set.of();

    @Override
    public void init(ComponentDef def, ComponentContext context) {
        super.init(def, context);
        parseStyle();
        repositionSlots();
    }

    @Override
    public void updateProps(Map<String, String> changedProps) {
        super.updateProps(changedProps);
        parseStyle();
        repositionSlots();
    }

    private void parseStyle() {
        // Nested loops below iterate rows*cols each frame; neither was bounded.
        rows = Math.clamp(parseInt("rows", 3), 0, MAX_GRID_SIDE);
        cols = Math.clamp(parseInt("cols", 9), 0, MAX_GRID_SIDE);
        startSlot = parseInt("start_slot", 0);
        lockedAbove = parseInt("locked_above", Integer.MAX_VALUE);
        slotStyle = parseString("slot_style", "beveled");
        dimSlots = parseSlotSet("dim_slots");
    }

    private Set<Integer> parseSlotSet(String key) {
        String val = props.get(key);
        if (val == null || val.isBlank()) return Set.of();
        Set<Integer> out = new HashSet<>();
        for (String part : val.split(",")) {
            try { out.add(Integer.parseInt(part.trim())); }
            catch (NumberFormatException ignored) { /* a bad entry veils nothing */ }
        }
        return out;
    }

    /**
     * Reposition vanilla Slot objects on the menu so items render at the right place.
     * Slot.x/y are relative to the container screen's leftPos/topPos.
     */
    private void repositionSlots() {
        if (context == null || context.menu() == null) return;

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int slotIndex = startSlot + row * cols + col;
                // Slot positions are relative to the screen origin (leftPos/topPos)
                // Our x/y are absolute, so subtract the screen offset
                int slotX = (x - context.screenX()) + col * CELL_SIZE + BORDER;
                int slotY = (y - context.screenY()) + row * CELL_SIZE + BORDER;
                context.menu().repositionSlot(slotIndex, slotX, slotY);
            }
        }
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int slotX = x + col * CELL_SIZE;
                int slotY = y + row * CELL_SIZE;
                int slotIndex = startSlot + row * cols + col;

                drawSlotBackground(graphics, slotX, slotY);

                if (slotIndex >= lockedAbove) {
                    graphics.fill(slotX + BORDER, slotY + BORDER,
                        slotX + BORDER + ITEM_SIZE, slotY + BORDER + ITEM_SIZE, LOCKED_OVERLAY);
                }
            }
        }
    }

    /**
     * The veil, drawn after vanilla has put the items in: a slot named in {@code dim_slots}
     * steps back behind a dark pane and the rest of the grid is what is left lit. Over the
     * item cell only, not the frame, so a run of veiled slots still reads as slots.
     */
    @Override
    public void renderOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (dimSlots.isEmpty()) return;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (!dimSlots.contains(startSlot + row * cols + col)) continue;
                int slotX = x + col * CELL_SIZE + BORDER;
                int slotY = y + row * CELL_SIZE + BORDER;
                graphics.fill(slotX, slotY, slotX + ITEM_SIZE, slotY + ITEM_SIZE, DIM_OVERLAY);
            }
        }
    }

    private void drawSlotBackground(GuiGraphicsExtractor graphics, int slotX, int slotY) {
        // "none": the backdrop already has slots in it and this grid is only placing the menu's
        // own slots over them. Drawing here would be a second frame on top of the first.
        if ("none".equals(slotStyle)) {
            return;
        }
        if ("beveled".equals(slotStyle)) {
            graphics.fill(slotX, slotY, slotX + CELL_SIZE, slotY + 1, SLOT_BORDER_DARK);
            graphics.fill(slotX, slotY, slotX + 1, slotY + CELL_SIZE, SLOT_BORDER_DARK);
            graphics.fill(slotX, slotY + CELL_SIZE - 1, slotX + CELL_SIZE, slotY + CELL_SIZE, SLOT_BORDER_LIGHT);
            graphics.fill(slotX + CELL_SIZE - 1, slotY, slotX + CELL_SIZE, slotY + CELL_SIZE, SLOT_BORDER_LIGHT);
            graphics.fill(slotX + 1, slotY + 1, slotX + CELL_SIZE - 1, slotY + CELL_SIZE - 1, SLOT_INNER);
        } else {
            graphics.fill(slotX, slotY, slotX + CELL_SIZE, slotY + CELL_SIZE, SLOT_INNER);
        }
    }
}
