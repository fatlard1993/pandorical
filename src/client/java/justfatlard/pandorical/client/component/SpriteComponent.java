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
    private int textureWidth;
    private int textureHeight;
    private int textureU;
    private int textureV;

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

    /**
     * Clip mode owns its size animation: the reveal must be re-clipped at the
     * interpolated width/height every frame, never scaled (see
     * AbstractComponent#selfRendersInterpolatedSize).
     */
    @Override
    public boolean selfRendersInterpolatedSize() {
        return texture != null && textureWidth > 0 && textureHeight > 0;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (texture != null) {
            if (textureWidth > 0 && textureHeight > 0) {
                // Native-size draw clipped to bounds; size interpolation is
                // applied here as a per-frame re-clip, a true reveal
                GeometrySnapshot g = interpolatedGeometry(delta);
                // Clamped against the region left of the source origin, so a sprite
                // revealing from a non-zero u/v can never sample past the texture
                int drawW = Math.min(Math.round(g.width()), textureWidth - textureU);
                int drawH = Math.min(Math.round(g.height()), textureHeight - textureV);
                if (drawW > 0 && drawH > 0) {
                    graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, textureU, textureV,
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
