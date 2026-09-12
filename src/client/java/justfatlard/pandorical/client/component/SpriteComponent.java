package justfatlard.pandorical.client.component;

import justfatlard.pandorical.protocol.ComponentDef;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * A colour fill, or a {@code texture} (full path with extension) stretched over the bounds.
 * With {@code texture_width} and {@code texture_height} it is drawn at native size, clipped.
 */
public class SpriteComponent extends AbstractComponent {
    private int color;
    private Identifier texture;
    private int textureWidth;
    private int textureHeight;
    private int textureU;
    private int textureV;
    // The source origin blends on the geometry's clock, so a bottom-anchored gauge that moves v
    // with height keeps its anchored edge pinned mid-blend.
    private float prevU;
    private float prevV;

    @Override
    public void init(ComponentDef def, ComponentContext context) {
        super.init(def, context);
        parseStyle();
        prevU = textureU;
        prevV = textureV;
    }

    @Override
    public void updateProps(Map<String, String> changedProps) {
        // Before super restarts the geometry blend.
        float t = geometryBlend(0f);
        prevU = prevU + (textureU - prevU) * t;
        prevV = prevV + (textureV - prevV) * t;
        super.updateProps(changedProps);
        parseStyle();
    }

    private void parseStyle() {
        color = parseColor("color", 0xFFFFFFFF);
        trackColor("color", color);
        String textureId = props.get("texture");
        texture = (textureId == null || textureId.isEmpty()) ? null : Identifier.tryParse(textureId);
        textureWidth = parseIntProp("texture_width");
        textureHeight = parseIntProp("texture_height");
        textureU = parseIntProp("texture_u");
        textureV = parseIntProp("texture_v");
    }

    private int parseIntProp(String key) {
        String value = props.get(key);
        if (value == null) return 0;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    @Override
    public boolean selfRendersInterpolatedSize() {
        return texture != null && textureWidth > 0 && textureHeight > 0;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (texture != null) {
            if (textureWidth > 0 && textureHeight > 0) {
                GeometrySnapshot g = interpolatedGeometry(delta);
                float t = geometryBlend(delta);
                int drawU = Math.round(prevU + (textureU - prevU) * t);
                int drawV = Math.round(prevV + (textureV - prevV) * t);
                int drawW = Math.min(Math.round(g.width()), textureWidth - drawU);
                int drawH = Math.min(Math.round(g.height()), textureHeight - drawV);
                if (drawW > 0 && drawH > 0) {
                    graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, drawU, drawV,
                        drawW, drawH, textureWidth, textureHeight);
                }
            } else {
                graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F,
                    width, height, width, height);
            }
            return;
        }
        int renderColor = interpolatedColor("color", 0xFFFFFFFF, delta);
        graphics.fill(x, y, x + width, y + height, renderColor);
    }
}
