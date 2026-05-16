package justfatlard.pandorical.client.component;

import justfatlard.pandorical.protocol.ComponentDef;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Static/dynamic text display. Supports color, shadow, word wrapping, line limits.
 */
public class TextComponent extends AbstractComponent {
    private String displayText;
    private int color;
    private boolean shadow;
    private int wrapWidth;
    private int maxLines;
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
        shadow = parseBool("shadow", false);
        wrapWidth = parseInt("wrap_width", 0);
        maxLines = parseInt("max_lines", 0);
        cachedLines = null; // invalidate on prop change
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (displayText.isEmpty()) return;

        if (wrapWidth > 0) {
            renderWrapped(graphics);
        } else {
            graphics.text(context.font(), displayText, x, y, color, shadow);
        }
    }

    private static final int LINE_HEIGHT = 11;

    private void renderWrapped(GuiGraphicsExtractor graphics) {
        if (cachedLines == null) {
            cachedLines = wrapText(displayText, wrapWidth);
            if (maxLines > 0 && cachedLines.size() > maxLines) {
                cachedLines = new ArrayList<>(cachedLines.subList(0, maxLines));
                String last = cachedLines.get(maxLines - 1);
                if (last.length() > 3) {
                    cachedLines.set(maxLines - 1, last.substring(0, last.length() - 3) + "...");
                }
            }
        }

        int lineY = y;
        for (String line : cachedLines) {
            graphics.text(context.font(), line, x, lineY, color, shadow);
            lineY += LINE_HEIGHT;
        }
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
