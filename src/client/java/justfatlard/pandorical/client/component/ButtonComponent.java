package justfatlard.pandorical.client.component;

import justfatlard.pandorical.protocol.ComponentDef;
import net.minecraft.world.scores.TeamColor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Map;
import justfatlard.pandorical.api.ComponentType;

/**
 * Clickable button that sends ScreenActionC2S on click.
 * Supports label text, translation keys, enabled/disabled state, and style variants.
 */
public class ButtonComponent extends AbstractComponent {
    private String label;
    private Identifier icon;
    private Component tooltip;
    private boolean enabled;
    private String style;
    private boolean hovered;

    /**
     * How far the icon sits inside the button's own edge. The sprite fills what is left, so art
     * cut for the button size it is used at draws one-to-one: a 10x10 icon on the 12px buttons a
     * row of controls is made of.
     */
    private static final int ICON_INSET = 1;

    // Colors
    /** Wide enough to read across a room, narrow enough not to crowd the label. */
    private static final int ACCENT_WIDTH = 3;

    private int accent;

    /**
     * The face is vanilla's own button, ninesliced from the GUI atlas, in vanilla's three states.
     * The first cut drew a flat grey fill with a one-pixel light/dark border, and every screen
     * built from it read as a mod's idea of a button sitting on a vanilla panel; nothing hand-drawn
     * here was going to keep up with the texture every other button on the client wears.
     */
    private static final Identifier SPRITE_NORMAL = Identifier.parse("minecraft:widget/button");
    private static final Identifier SPRITE_HOVER = Identifier.parse("minecraft:widget/button_highlighted");
    private static final Identifier SPRITE_DISABLED = Identifier.parse("minecraft:widget/button_disabled");
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
        String iconId = props.get(ComponentType.PROP_ICON);
        icon = (iconId == null || iconId.isEmpty()) ? null : Identifier.tryParse(iconId);
        String tooltipKey = props.get(ComponentType.PROP_TOOLTIP_KEY);
        String tooltipText = props.get(ComponentType.PROP_TOOLTIP);
        tooltip = tooltipKey != null && !tooltipKey.isEmpty() ? Component.translatable(tooltipKey)
            : tooltipText != null && !tooltipText.isEmpty() ? Component.literal(tooltipText) : null;
        enabled = parseBool("enabled", true);
        style = parseString("style", "default");
        // 0 means no accent, which is the default and the common case.
        accent = parseColor("accent", 0);
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        hovered = isMouseOver(mouseX, mouseY) && enabled;
        // Said even over a disabled button: what a thing would do is worth knowing when it will not.
        if (tooltip != null && isMouseOver(mouseX, mouseY)) graphics.setTooltipForNextFrame(tooltip, mouseX, mouseY);

        // "pressed" is the choice already made: the sunken face, with its label still readable.
        boolean pressed = enabled && "pressed".equals(style);
        Identifier face = !enabled || pressed ? SPRITE_DISABLED : hovered ? SPRITE_HOVER : SPRITE_NORMAL;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, face, x, y, width, height);

        // Accent: a bar down the leading edge rather than a recoloured button.
        // The face stays the one vanilla button every screen shares, so a set of
        // controls reads as one set and the colour says what kind of thing this
        // one does. Inset past the sprite's own edge so it cannot be mistaken
        // for a selection highlight.
        if (accent != 0 && enabled) {
            graphics.fill(x + 2, y + 2, x + 2 + ACCENT_WIDTH, y + height - 2, accent);
        }

        if (icon != null) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon,
                x + ICON_INSET, y + ICON_INSET,
                width - ICON_INSET * 2, height - ICON_INSET * 2);
            return;
        }

        // Text
        int textColor;
        if (!enabled) {
            textColor = TEXT_DISABLED;
        } else if (pressed) {
            textColor = 0xFFFFFF80;
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
