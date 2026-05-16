package justfatlard.pandorical.client.component;

import justfatlard.pandorical.protocol.ComponentDef;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Map;

/**
 * Renders colored rectangles or textured quads.
 * Used for indicators, dividers, backgrounds, and decorative elements.
 */
public class SpriteComponent extends AbstractComponent {
    private int color;

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
        color = parseColor("color", 0xFFFFFFFF);
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(x, y, x + width, y + height, color);
    }
}
