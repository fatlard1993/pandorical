package justfatlard.pandorical.client.component;

import justfatlard.pandorical.api.ComponentType;
import justfatlard.pandorical.protocol.ComponentDef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for components. Handles common fields, prop parsing, and client-side
 * interpolation of mutable numeric props (geometry, scale, rotation, and opt-in colors).
 *
 * <p><b>Interpolation model</b>: mirrors the technique {@code StructureManager} already uses
 * for structure pose interpolation: every time a numeric prop changes (via {@link #updateProps}),
 * the value the component is currently displaying (which may itself be mid-interpolation) is
 * captured as the new "previous" snapshot, the freshly-applied value becomes the new "target",
 * and a short fixed tick window ({@link #INTERPOLATION_TICKS}) blends between them. {@link #tick()}
 * advances that window once per client tick; render-time callers sample the blend with the
 * current partial tick. Geometry (x/y/width/height) and scale/rotation are tracked unconditionally
 * for every component so that {@code ScreenHelper}'s render-time transform wrapper can smooth them
 * generically for all component types, not just sprite/text; see {@code ScreenHelper.renderComponentTree}.
 */
public abstract class AbstractComponent implements PandoricalComponent {
    /**
     * Ticks over which a new value is blended in. Kept short and fixed rather than derived from
     * the actual interval between server updates, same simplification {@code StructureManager}
     * documents for pose interpolation; adequate as long as callers update roughly once per
     * server tick (≤20/sec).
     */
    protected static final int INTERPOLATION_TICKS = 3;

    protected String id;
    protected int x, y, width, height;
    protected Map<String, String> props = new HashMap<>();
    protected ComponentContext context;
    protected final List<PandoricalComponent> children = new ArrayList<>();

    // --- Geometry/scale/rotation interpolation ---

    protected float scale = 1f;
    protected float rotation = 0f;

    private GeometrySnapshot previousGeom;
    private GeometrySnapshot targetGeom;
    private int geomTicksSinceUpdate = INTERPOLATION_TICKS; // start "arrived" — no bogus lerp-in on first render

    /** Interpolated geometry sample: x/y/width/height/scale/rotation. */
    public record GeometrySnapshot(float x, float y, float width, float height, float scale, float rotation) {}

    // --- Opt-in per-key color interpolation (subclasses call trackColor/interpolatedColor) ---

    private final Map<String, ColorAnim> colorAnims = new HashMap<>();

    private static final class ColorAnim {
        int previous, target, ticksSinceUpdate;
        ColorAnim(int previous, int target, int ticksSinceUpdate) {
            this.previous = previous; this.target = target; this.ticksSinceUpdate = ticksSinceUpdate;
        }
    }

    @Override
    public void init(ComponentDef def, ComponentContext context) {
        this.id = def.id();
        this.x = def.x();
        this.y = def.y();
        this.width = def.width();
        this.height = def.height();
        this.props.putAll(def.props());
        this.context = context;

        parseGeometryStyle();
        GeometrySnapshot initial = currentGeometrySnapshot();
        this.previousGeom = initial;
        this.targetGeom = initial;
    }

    @Override
    public void updateProps(Map<String, String> changedProps) {
        // Capture wherever this component visually is RIGHT NOW (possibly mid-blend) as the new
        // interpolation start point, so a steady stream of updates blends continuously instead of
        // stair-stepping; same approach as StructureManager.ClientStructure.pushPose().
        GeometrySnapshot before = interpolatedGeometry(0f);

        this.props.putAll(changedProps);

        // Geometry keys are recognized directly out of the shared string prop map rather than
        // requiring a separate typed wire field; see the design note on ComponentType's
        // PROP_X/PROP_Y/PROP_WIDTH/PROP_HEIGHT constants.
        if (changedProps.containsKey(ComponentType.PROP_X)) this.x = parseInt(ComponentType.PROP_X, this.x);
        if (changedProps.containsKey(ComponentType.PROP_Y)) this.y = parseInt(ComponentType.PROP_Y, this.y);
        if (changedProps.containsKey(ComponentType.PROP_WIDTH)) this.width = parseInt(ComponentType.PROP_WIDTH, this.width);
        if (changedProps.containsKey(ComponentType.PROP_HEIGHT)) this.height = parseInt(ComponentType.PROP_HEIGHT, this.height);

        parseGeometryStyle();

        this.previousGeom = before;
        this.targetGeom = currentGeometrySnapshot();
        this.geomTicksSinceUpdate = 0;
    }

    private void parseGeometryStyle() {
        scale = parseFloat(ComponentType.PROP_SCALE, 1f);
        rotation = parseFloat(ComponentType.PROP_ROTATION, 0f);
    }

    private GeometrySnapshot currentGeometrySnapshot() {
        return new GeometrySnapshot(x, y, width, height, scale, rotation);
    }

    /** Advance interpolation progress by one client tick. Called once/tick via HudManager/ScreenHelper. */
    @Override
    public void tick() {
        if (geomTicksSinceUpdate < INTERPOLATION_TICKS) geomTicksSinceUpdate++;
        for (ColorAnim anim : colorAnims.values()) {
            if (anim.ticksSinceUpdate < INTERPOLATION_TICKS) anim.ticksSinceUpdate++;
        }
    }

    /** Interpolated geometry for the current render frame. Used by ScreenHelper's transform wrapper. */
    public GeometrySnapshot interpolatedGeometry(float partialTick) {
        float t = clamp01((geomTicksSinceUpdate + partialTick) / INTERPOLATION_TICKS);
        if (t >= 1f) return targetGeom;
        if (t <= 0f) return previousGeom;
        float ix = lerp(t, previousGeom.x(), targetGeom.x());
        float iy = lerp(t, previousGeom.y(), targetGeom.y());
        float iw = lerp(t, previousGeom.width(), targetGeom.width());
        float ih = lerp(t, previousGeom.height(), targetGeom.height());
        float is = lerp(t, previousGeom.scale(), targetGeom.scale());
        float ir = lerpAngle(t, previousGeom.rotation(), targetGeom.rotation());
        return new GeometrySnapshot(ix, iy, iw, ih, is, ir);
    }

    // --- Opt-in color interpolation helpers ---

    /**
     * Record the logical (target) color for {@code key} so future {@link #interpolatedColor}
     * calls blend smoothly towards it. Call from a subclass's style-parsing method every time
     * props are (re)parsed, e.g. {@code trackColor("color", parseColor("color", 0xFFFFFFFF))}.
     */
    protected void trackColor(String key, int newColor) {
        ColorAnim anim = colorAnims.get(key);
        if (anim == null) {
            colorAnims.put(key, new ColorAnim(newColor, newColor, INTERPOLATION_TICKS));
            return;
        }
        if (anim.target == newColor) return;
        float t = clamp01(anim.ticksSinceUpdate / (float) INTERPOLATION_TICKS);
        int current = t >= 1f ? anim.target : lerpArgb(anim.previous, anim.target, t);
        anim.previous = current;
        anim.target = newColor;
        anim.ticksSinceUpdate = 0;
    }

    /** Interpolated color for {@code key} at the current partial tick, falling back to a plain parse if untracked. */
    protected int interpolatedColor(String key, int defaultColor, float partialTick) {
        ColorAnim anim = colorAnims.get(key);
        if (anim == null) return parseColor(key, defaultColor);
        float t = clamp01((anim.ticksSinceUpdate + partialTick) / INTERPOLATION_TICKS);
        return t >= 1f ? anim.target : lerpArgb(anim.previous, anim.target, t);
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    private static float lerp(float t, float start, float end) {
        return start + t * (end - start);
    }

    private static float lerpAngle(float t, float start, float end) {
        return start + t * wrapDegrees(end - start);
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0f;
        if (wrapped >= 180.0f) wrapped -= 360.0f;
        if (wrapped < -180.0f) wrapped += 360.0f;
        return wrapped;
    }

    private static int lerpArgb(int from, int to, float t) {
        int a = lerpChannel((from >>> 24) & 0xFF, (to >>> 24) & 0xFF, t);
        int r = lerpChannel((from >>> 16) & 0xFF, (to >>> 16) & 0xFF, t);
        int g = lerpChannel((from >>> 8) & 0xFF, (to >>> 8) & 0xFF, t);
        int b = lerpChannel(from & 0xFF, to & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerpChannel(int from, int to, float t) {
        return Math.round(from + t * (to - from));
    }

    @Override
    public String getId() { return id; }
    @Override
    public int getX() { return x; }
    @Override
    public int getY() { return y; }
    @Override
    public int getWidth() { return width; }
    @Override
    public int getHeight() { return height; }
    @Override
    public List<PandoricalComponent> getChildren() { return children; }

    // --- Prop parsing helpers ---

    protected int parseColor(String key, int defaultColor) {
        String val = props.get(key);
        if (val == null) return defaultColor;
        try {
            // Support #RRGGBB and #AARRGGBB
            if (val.startsWith("#")) val = val.substring(1);
            long parsed = Long.parseLong(val, 16);
            if (val.length() <= 6) parsed |= 0xFF000000L; // add full alpha
            return (int) parsed;
        } catch (NumberFormatException e) {
            return defaultColor;
        }
    }

    protected boolean parseBool(String key, boolean defaultVal) {
        String val = props.get(key);
        if (val == null) return defaultVal;
        return "true".equalsIgnoreCase(val);
    }

    protected int parseInt(String key, int defaultVal) {
        String val = props.get(key);
        if (val == null) return defaultVal;
        try { return Integer.parseInt(val); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    protected float parseFloat(String key, float defaultVal) {
        String val = props.get(key);
        if (val == null) return defaultVal;
        try { return Float.parseFloat(val); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    protected String parseString(String key, String defaultVal) {
        return props.getOrDefault(key, defaultVal);
    }
}
