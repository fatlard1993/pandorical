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
    /** Wide enough to read across a room, narrow enough not to crowd the label. */
    private static final int ACCENT_WIDTH = 3;

    private int accent;

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
        // 0 means no accent, which is the default and the common case.
        accent = parseColor("accent", 0);
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

        // Accent: a bar down the leading edge rather than a recoloured button.
        // The background stays the one grey every Pandorical button shares, so a
        // screen reads as one set of controls and the colour says what kind of
        // thing this one does. Sits inside the border so it cannot be mistaken
        // for a selection highlight.
        if (accent != 0 && enabled) {
            graphics.fill(x + 1, y + 1, x + 1 + ACCENT_WIDTH, y + height - 1, accent);
        }

        // Text
        int textColor;
        if (!enabled) {
            textColor = TEXT_DISABLED;
        } else if ("accepted".equals(style)) {
            textColor = 0xFF000000 | TeamColor.GREEN.rgb();
        } else {
            textColor = TEXT_NORMAL;
        }

        drawLabel(graphics, textColor);
    }

    /** Room left for the label once the button's own edges are accounted for. */
    private static final int LABEL_INSET = 4;

    /** Below this the text is too small to read, and trimming is the lesser loss. */
    private static final float MIN_LABEL_SCALE = 0.55F;

    private static final String ELLIPSIS = "...";

    /**
     * Draw the label so that all of it is on the button.
     *
     * <p>It used to be centred at full size and left to overrun: a label wider than its button
     * spilled past both ends, and inside a scrolling list the clip cut it off mid-word. A button
     * whose text you cannot finish reading is a button you cannot choose from.
     *
     * <p>Shrinking is tried before trimming, because the end of a sentence is usually the half that
     * says what the choice actually does. Only when it is still too wide at the smallest readable
     * size does it lose its tail.
     */
    private void drawLabel(GuiGraphicsExtractor graphics, int textColor) {
        var font = context.font();
        int room = Math.max(1, width - LABEL_INSET * 2);

        String shown = label;
        int textWidth = font.width(shown);
        float scale = 1.0F;

        if (textWidth > room) {
            scale = Math.max(MIN_LABEL_SCALE, room / (float) textWidth);
            while (shown.length() > ELLIPSIS.length() && font.width(shown + ELLIPSIS) * scale > room) {
                shown = shown.substring(0, shown.length() - 1);
            }
            if (!shown.equals(label)) shown = shown + ELLIPSIS;
            textWidth = font.width(shown);
        }

        float centerX = x + width / 2.0F;
        float centerY = y + height / 2.0F;

        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(centerX, centerY);
        pose.scale(scale, scale);
        graphics.text(font, shown, -textWidth / 2, -4, textColor, true);
        pose.popMatrix();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (enabled && isMouseOver(mouseX, mouseY)) {
            context.sendAction().accept(id, Map.of("button", String.valueOf(button)));
            return true;
        }
        return false;
    }

    // Tracks enabled rather than returning a blanket true: mouseClicked above
    // ignores a disabled button, so navigating onto one would strand the
    // player on a target that does nothing when pressed.
    @Override
    public boolean isNavigable() {
        return enabled;
    }
}
