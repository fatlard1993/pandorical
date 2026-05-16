package justfatlard.pandorical.client.component;

import justfatlard.pandorical.protocol.ComponentDef;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Map;

/**
 * Renders a grid of item slots and positions the menu's Slot objects to match.
 * This is the bridge between declarative UI and vanilla container slot sync.
 *
 * On init, repositions the corresponding Slot objects in PandoricalMenu so that
 * vanilla renders items at the correct positions.
 */
public class InventoryGridComponent extends AbstractComponent {
    private static final int CELL_SIZE = 18;
    private static final int SLOT_SIZE = 16;
    private static final int SLOT_BORDER_DARK = 0xFF373737;
    private static final int SLOT_BORDER_LIGHT = 0xFFFFFFFF;
    private static final int SLOT_INNER = 0xFF8B8B8B;
    private static final int LOCKED_OVERLAY = 0xCC1A1A1A;

    private int rows, cols, startSlot, lockedAbove;
    private String slotStyle;

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
        rows = parseInt("rows", 3);
        cols = parseInt("cols", 9);
        startSlot = parseInt("start_slot", 0);
        lockedAbove = parseInt("locked_above", Integer.MAX_VALUE);
        slotStyle = parseString("slot_style", "beveled");
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
                int slotX = (x - context.screenX()) + col * CELL_SIZE + 1; // +1 for border
                int slotY = (y - context.screenY()) + row * CELL_SIZE + 1;
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
                    graphics.fill(slotX + 1, slotY + 1, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1, LOCKED_OVERLAY);
                }
            }
        }
    }

    private void drawSlotBackground(GuiGraphicsExtractor graphics, int slotX, int slotY) {
        if ("beveled".equals(slotStyle)) {
            graphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + 1, SLOT_BORDER_DARK);
            graphics.fill(slotX, slotY, slotX + 1, slotY + SLOT_SIZE, SLOT_BORDER_DARK);
            graphics.fill(slotX, slotY + SLOT_SIZE - 1, slotX + SLOT_SIZE, slotY + SLOT_SIZE, SLOT_BORDER_LIGHT);
            graphics.fill(slotX + SLOT_SIZE - 1, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, SLOT_BORDER_LIGHT);
            graphics.fill(slotX + 1, slotY + 1, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1, SLOT_INNER);
        } else {
            graphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, SLOT_INNER);
        }
    }
}
