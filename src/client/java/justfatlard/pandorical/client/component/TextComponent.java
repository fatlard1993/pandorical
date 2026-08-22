package justfatlard.pandorical.client.component;

import justfatlard.pandorical.protocol.ComponentDef;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Static/dynamic text display. Supports color, shadow, word wrapping, line
 * limits, and horizontal alignment within the component's own width.
 */
public class TextComponent extends AbstractComponent {
    private String displayText;
    private int color;
    private boolean shadow;
    private int wrapWidth;
    private int maxLines;
    private String align;
    private List<String> cachedLines;

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
        String textKey = props.get("text_key");
        if (textKey != null) {
            displayText = Component.translatable(textKey).getString();
        } else {
            displayText = parseString("text", "");
        }
        color = parseColor("color", 0xFFFFFFFF);
        trackColor("color", color);
        shadow = parseBool("shadow", false);
        wrapWidth = parseInt("wrap_width", 0);
        maxLines = parseInt("max_lines", 0);
        align = parseString("align", "left");
        cachedLines = null; // invalidate on prop change
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (displayText.isEmpty()) return;

        int renderColor = interpolatedColor("color", 0xFFFFFFFF, delta);
        if (wrapWidth > 0) {
            renderWrapped(graphics, renderColor);
        } else {
            graphics.text(context.font(), displayText, alignedX(displayText), y, renderColor, shadow);
        }
    }

    /**
     * Where a line starts, given the alignment. Only the client can answer this:
     * text length in pixels depends on the font and on the language the player
     * reads in, neither of which a server knows.
     */
    private int alignedX(String line) {
        return switch (align) {
            case "center" -> x + (width - context.font().width(line)) / 2;
            case "right" -> x + width - context.font().width(line);
            default -> x;
        };
    }

    private static final int LINE_HEIGHT = 11;

    private void renderWrapped(GuiGraphicsExtractor graphics, int renderColor) {
        if (cachedLines == null) {
            cachedLines = wrapText(displayText, wrapWidth);
            if (maxLines > 0 && cachedLines.size() > maxLines) {
                cachedLines = new ArrayList<>(cachedLines.subList(0, maxLines));
                cachedLines.set(maxLines - 1, ellipsise(cachedLines.get(maxLines - 1)));
            }
        }

        int lineY = y;
        for (String line : cachedLines) {
            graphics.text(context.font(), line, alignedX(line), lineY, renderColor, shadow);
            lineY += LINE_HEIGHT;
        }
    }

    private static final String ELLIPSIS = "...";

    /**
     * Marks a line as cut short, giving up only as many characters as the mark actually costs.
     *
     * <p>The line handed here already fits: it is the lines after it that are being dropped. Paying
     * for the mark in characters rather than pixels was throwing away three every time, so a name
     * with room to spare still arrived shortened, and one whose last characters were wide could
     * still overrun the edge it was being trimmed to fit.
     */
    private String ellipsise(String line) {
        String trimmed = line;
        while (!trimmed.isEmpty() && context.font().width(trimmed + ELLIPSIS) > wrapWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + ELLIPSIS;
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine.isEmpty() ? word : currentLine + " " + word;
            if (context.font().width(testLine) <= maxWidth) {
                if (!currentLine.isEmpty()) currentLine.append(" ");
                currentLine.append(word);
            } else {
                if (!currentLine.isEmpty()) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                } else {
                    lines.add(word);
                }
            }
        }
        if (!currentLine.isEmpty()) lines.add(currentLine.toString());
        return lines;
    }
}
