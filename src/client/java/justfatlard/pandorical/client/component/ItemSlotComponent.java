package justfatlard.pandorical.client.component;

import justfatlard.pandorical.protocol.ComponentDef;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Map;

/**
 * Renders a single item slot background with optional lock overlay.
 * When used inside a container screen, the actual item rendering is handled
 * by vanilla's slot overlay system — this just draws the visual frame.
 *
 * Rendering pattern from BackpackInventoryScreen.drawSlotBackground().
 */
public class ItemSlotComponent extends AbstractComponent {
    private static final int SLOT_BORDER_DARK = 0xFF373737;
    private static final int SLOT_BORDER_LIGHT = 0xFFFFFFFF;
    private static final int SLOT_INNER = 0xFF8B8B8B;
    private static final int LOCKED_OVERLAY = 0xCC1A1A1A;

    private int slotIndex;
    private boolean locked;
    private String slotStyle;

    @Override
    public void init(ComponentDef def, ComponentContext context) {
        super.init(def, context);
        // Standard slot is 16x16 inner, 18x18 with border in beveled style
        if (width == 0) width = 16;
        if (height == 0) height = 16;
        parseStyle();
    }

    @Override
    public void updateProps(Map<String, String> changedProps) {
        super.updateProps(changedProps);
        parseStyle();
    }

    private void parseStyle() {
        slotIndex = parseInt("slot_index", -1);
        locked = parseBool("locked", false);
        slotStyle = parseString("slot_style", "beveled");
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if ("beveled".equals(slotStyle)) {
            // Top and left border (dark)
            graphics.fill(x, y, x + width, y + 1, SLOT_BORDER_DARK);
            graphics.fill(x, y, x + 1, y + height, SLOT_BORDER_DARK);
            // Bottom and right border (light)
            graphics.fill(x, y + height - 1, x + width, y + height, SLOT_BORDER_LIGHT);
            graphics.fill(x + width - 1, y, x + width, y + height, SLOT_BORDER_LIGHT);
            // Inner
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, SLOT_INNER);
        } else {
            graphics.fill(x, y, x + width, y + height, SLOT_INNER);
        }

        if (locked) {
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, LOCKED_OVERLAY);
        }
    }
}
