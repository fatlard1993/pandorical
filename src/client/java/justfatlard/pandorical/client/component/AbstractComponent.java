package justfatlard.pandorical.client.component;

import justfatlard.pandorical.api.ComponentType;
import justfatlard.pandorical.protocol.ComponentDef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for components: common fields, prop parsing, and interpolation of geometry, scale,
 * rotation and opt-in colors. On each change the displayed value, possibly mid-blend, becomes the
 * start and the new value the target, blended over {@link #interpolationTicks} client ticks.
 * Geometry is tracked for every component so {@code ScreenHelper} can smooth it generically.
 */
public abstract class AbstractComponent implements PandoricalComponent {
    /** Fixed, not derived from the update interval; suits updates at most once per server tick. */
    protected static final int INTERPOLATION_TICKS = 3;

    /** From the {@code interp_ticks} prop. */
    protected int interpolationTicks = INTERPOLATION_TICKS;

    protected String id;
    /** Screen position; the server's x and y are relative to {@link #originX}, {@link #originY}. */
    protected int x, y, width, height;
    private int originX, originY;
    protected Map<String, String> props = new HashMap<>();
    protected ComponentContext context;
    protected final List<PandoricalComponent> children = new ArrayList<>();

    protected float scale = 1f;
    protected float rotation = 0f;
    protected boolean visible = true;

    private GeometrySnapshot previousGeom;
    private GeometrySnapshot targetGeom;
    private int geomTicksSinceUpdate = INTERPOLATION_TICKS;

    public record GeometrySnapshot(float x, float y, float width, float height, float scale, float rotation) {}

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
        this.interpolationTicks = Math.max(1, parseInt("interp_ticks", INTERPOLATION_TICKS));

        parseGeometryStyle();
        GeometrySnapshot initial = currentGeometrySnapshot();
        this.previousGeom = initial;
        this.targetGeom = initial;
    }

    @Override
    public void updateProps(Map<String, String> changedProps) {
        GeometrySnapshot before = interpolatedGeometry(0f);

        this.props.putAll(changedProps);

        int wasX = x;
        int wasY = y;
        if (changedProps.containsKey(ComponentType.PROP_X)) this.x = originX + parseInt(ComponentType.PROP_X, this.x - originX);
        if (changedProps.containsKey(ComponentType.PROP_Y)) this.y = originY + parseInt(ComponentType.PROP_Y, this.y - originY);
        if (changedProps.containsKey(ComponentType.PROP_WIDTH)) this.width = parseInt(ComponentType.PROP_WIDTH, this.width);
        if (changedProps.containsKey(ComponentType.PROP_HEIGHT)) this.height = parseInt(ComponentType.PROP_HEIGHT, this.height);

        parseGeometryStyle();

        this.previousGeom = before;
        this.targetGeom = currentGeometrySnapshot();
        this.geomTicksSinceUpdate = 0;
        if (x != wasX || y != wasY) {
            moved();
            for (PandoricalComponent child : children) child.shiftOrigin(x - wasX, y - wasY);
        }
    }

    @Override
    public void placeIn(int originX, int originY) {
        this.originX = originX;
        this.originY = originY;
    }

    @Override
    public void shiftOrigin(int dx, int dy) {
        GeometrySnapshot before = interpolatedGeometry(0f);
        originX += dx;
        originY += dy;
        x += dx;
        y += dy;
        previousGeom = before;
        targetGeom = currentGeometrySnapshot();
        geomTicksSinceUpdate = 0;
        moved();
        for (PandoricalComponent child : children) child.shiftOrigin(dx, dy);
    }

    /** For a component holding something positioned with it, such as a vanilla widget. */
    protected void moved() {}

    private void parseGeometryStyle() {
        scale = parseFloat(ComponentType.PROP_SCALE, 1f);
        rotation = parseFloat(ComponentType.PROP_ROTATION, 0f);
        visible = parseBool(ComponentType.PROP_VISIBLE, true);
    }

    @Override
    public boolean isVisible() { return visible; }

    private GeometrySnapshot currentGeometrySnapshot() {
        return new GeometrySnapshot(x, y, width, height, scale, rotation);
    }

    @Override
    public void tick() {
        if (geomTicksSinceUpdate < interpolationTicks) geomTicksSinceUpdate++;
        for (ColorAnim anim : colorAnims.values()) {
            if (anim.ticksSinceUpdate < interpolationTicks) anim.ticksSinceUpdate++;
        }
    }

    /**
     * True for a component that draws its own interpolated width and height, so ScreenHelper
     * skips the size scale: for one where size means how much is revealed, not how large.
     */
    public boolean selfRendersInterpolatedSize() {
        return false;
    }

    /** 0..1 through the geometry blend, for props that must move in lockstep with geometry. */
    protected float geometryBlend(float partialTick) {
        return clamp01((geomTicksSinceUpdate + partialTick) / (float) interpolationTicks);
    }

    public GeometrySnapshot interpolatedGeometry(float partialTick) {
        float t = geometryBlend(partialTick);
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

    /**
     * The geometry drawn: {@link #interpolatedGeometry} plus any component-local addition. Add
     * here, not there: that one seeds the next blend, and would carry the addition twice.
     */
    public GeometrySnapshot displayedGeometry(float partialTick) {
        return interpolatedGeometry(partialTick);
    }

    /** Sets the target color {@link #interpolatedColor} blends toward; call on every prop parse. */
    protected void trackColor(String key, int newColor) {
        ColorAnim anim = colorAnims.get(key);
        if (anim == null) {
            colorAnims.put(key, new ColorAnim(newColor, newColor, INTERPOLATION_TICKS));
            return;
        }
        if (anim.target == newColor) return;
        float t = clamp01(anim.ticksSinceUpdate / (float) interpolationTicks);
        int current = t >= 1f ? anim.target : lerpArgb(anim.previous, anim.target, t);
        anim.previous = current;
        anim.target = newColor;
        anim.ticksSinceUpdate = 0;
    }

    /** Falls back to a plain parse for an untracked key. */
    protected int interpolatedColor(String key, int defaultColor, float partialTick) {
        ColorAnim anim = colorAnims.get(key);
        if (anim == null) return parseColor(key, defaultColor);
        float t = clamp01((anim.ticksSinceUpdate + partialTick) / (float) interpolationTicks);
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

    /** Hex {@code RRGGBB} (opaque) or {@code AARRGGBB}, with or without a leading {@code #}. */
    protected int parseColor(String key, int defaultColor) {
        String val = props.get(key);
        if (val == null) return defaultColor;
        try {
            if (val.startsWith("#")) val = val.substring(1);
            long parsed = Long.parseLong(val, 16);
            if (val.length() <= 6) parsed |= 0xFF000000L;
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
