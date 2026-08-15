package justfatlard.pandorical.client.component;

import justfatlard.pandorical.protocol.ComponentDef;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * Renders colored rectangles or textured quads.
 * Used for indicators, dividers, backgrounds, and decorative elements.
 *
 * <p>With a {@code texture} prop (full identifier including extension, e.g.
 * {@code "mymod:textures/gui/icon.png"}) the texture is stretched over the
 * component bounds; without one, or when the id fails to parse, the sprite
 * falls back to its color fill. A missing texture renders the vanilla
 * missing-texture pattern rather than crashing.
 */
public class SpriteComponent extends AbstractComponent {
    private int color;
    private Identifier texture;

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
        trackColor("color", color);
        String textureId = props.get("texture");
        texture = (textureId == null || textureId.isEmpty()) ? null : Identifier.tryParse(textureId);
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (texture != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F,
                width, height, width, height);
            return;
        }
        int renderColor = interpolatedColor("color", 0xFFFFFFFF, delta);
        graphics.fill(x, y, x + width, y + height, renderColor);
    }
}
