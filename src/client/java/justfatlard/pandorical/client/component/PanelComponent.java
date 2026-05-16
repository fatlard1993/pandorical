package justfatlard.pandorical.client.component;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Map;

/**
 * Background panel with optional beveled borders.
 * Rendering pattern from BackpackInventoryScreen.drawPanel().
 */
public class PanelComponent extends AbstractComponent {
    private int background;
    private String borderStyle;
    private int borderLight, borderDark, borderMidLight, borderMidDark;
    private int borderColor;

    @Override
    public void init(justfatlard.pandorical.protocol.ComponentDef def, ComponentContext context) {
        super.init(def, context);
        parseStyle();
    }

    @Override
    public void updateProps(Map<String, String> changedProps) {
        super.updateProps(changedProps);
        parseStyle();
    }

    private void parseStyle() {
        background = parseColor("background", 0xFFC6C6C6);
        borderStyle = parseString("border", "beveled");
        borderLight = parseColor("border_light", 0xFFFFFFFF);
        borderDark = parseColor("border_dark", 0xFF555555);
        borderMidLight = parseColor("border_mid_light", 0xFFAAAAAA);
        borderMidDark = parseColor("border_mid_dark", 0xFF7A7A7A);
        borderColor = parseColor("border_color", 0xFF555555);
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(x, y, x + width, y + height, background);

        if ("beveled".equals(borderStyle)) {
            // Outer border
            graphics.fill(x, y, x + width, y + 2, borderLight);
            graphics.fill(x, y, x + 2, y + height, borderLight);
            graphics.fill(x, y + height - 2, x + width, y + height, borderDark);
            graphics.fill(x + width - 2, y, x + width, y + height, borderDark);
            // Inner border
            graphics.fill(x + 2, y + 2, x + width - 2, y + 4, borderMidLight);
            graphics.fill(x + 2, y + 2, x + 4, y + height - 2, borderMidLight);
            graphics.fill(x + 2, y + height - 4, x + width - 2, y + height - 2, borderMidDark);
            graphics.fill(x + width - 4, y + 2, x + width - 2, y + height - 2, borderMidDark);
        } else if ("flat".equals(borderStyle)) {
            graphics.fill(x, y, x + width, y + 1, borderColor);
            graphics.fill(x, y + height - 1, x + width, y + height, borderColor);
            graphics.fill(x, y, x + 1, y + height, borderColor);
            graphics.fill(x + width - 1, y, x + width, y + height, borderColor);
        }
    }
}
