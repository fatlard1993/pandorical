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
    // The source origin blends on the geometry's own clock: a reveal that moves its v in step with
    // its height (a bottom-anchored gauge) must see both interpolate together, or the anchored edge
    // detaches for the length of the blend window
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
        // Capture the origin currently displayed (possibly mid-blend) before the geometry blend
        // restarts, same in-flight capture AbstractComponent does for geometry itself
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
                // The origin blends on the same clock as the geometry: a server that moves v with
                // height (v = textureHeight - height, the bottom-anchored gauge) then sees
                // textureHeight - v == height at every point of the blend, keeping the anchored
                // edge pinned instead of gapping while the size clamp below chases a snapped origin
                float t = geometryBlend(delta);
                int drawU = Math.round(prevU + (textureU - prevU) * t);
                int drawV = Math.round(prevV + (textureV - prevV) * t);
                // Clamped against the region left of the source origin, so a sprite
                // revealing from a non-zero u/v can never sample past the texture
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
