package justfatlard.pandorical.client.component;

import justfatlard.pandorical.protocol.ComponentDef;
import net.minecraft.world.scores.TeamColor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.Map;

/**
 * Clickable button that sends ScreenActionC2S on click.
 * Supports label text, translation keys, enabled/disabled state, and style variants.
 */
public class ButtonComponent extends AbstractComponent {
    private String label;
    private boolean enabled;
    private String style;
    private boolean hovered;

    // Colors
    private static final int BG_NORMAL = 0xFF666666;
    private static final int BG_HOVER = 0xFF7A7A7A;
    private static final int BG_DISABLED = 0xFF444444;
    private static final int BORDER_LIGHT = 0xFFAAAAAA;
    private static final int BORDER_DARK = 0xFF333333;
    private static final int TEXT_NORMAL = 0xFFFFFFFF;
    private static final int TEXT_DISABLED = 0xFFA0A0A0;

    @Override
    public void init(ComponentDef def, ComponentContext context) {
        super.init(def, context);
        parseStyle();
    }

    @Override
    public void updateProps(Map<String, String> changedProps) {
        super.updateProps(changedProps);
        parseStyle();
    }

    private void parseStyle() {
        // Translation key takes precedence over literal
        String labelKey = props.get("label_key");
        if (labelKey != null) {
            label = Component.translatable(labelKey).getString();
        } else {
            label = parseString("label", "");
        }
        enabled = parseBool("enabled", true);
        style = parseString("style", "default");
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        hovered = isMouseOver(mouseX, mouseY) && enabled;

        int bgColor = !enabled ? BG_DISABLED : hovered ? BG_HOVER : BG_NORMAL;
        graphics.fill(x, y, x + width, y + height, bgColor);

        // Border
        graphics.fill(x, y, x + width, y + 1, BORDER_LIGHT);
        graphics.fill(x, y, x + 1, y + height, BORDER_LIGHT);
        graphics.fill(x, y + height - 1, x + width, y + height, BORDER_DARK);
        graphics.fill(x + width - 1, y, x + width, y + height, BORDER_DARK);

        // Text
        int textColor;
        if (!enabled) {
            textColor = TEXT_DISABLED;
        } else if ("accepted".equals(style)) {
            textColor = 0xFF000000 | TeamColor.GREEN.rgb();
        } else {
            textColor = TEXT_NORMAL;
        }

        int textWidth = context.font().width(label);
        int textX = x + (width - textWidth) / 2;
        int textY = y + (height - 8) / 2;
        graphics.text(context.font(), label, textX, textY, textColor, true);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (enabled && isMouseOver(mouseX, mouseY)) {
            context.sendAction().accept(id, Map.of("button", String.valueOf(button)));
            return true;
        }
        return false;
    }
}
