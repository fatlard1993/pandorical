package justfatlard.pandorical.client.component;

import justfatlard.pandorical.protocol.ComponentDef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for components. Handles common fields and prop parsing.
 */
public abstract class AbstractComponent implements PandoricalComponent {
    protected String id;
    protected int x, y, width, height;
    protected Map<String, String> props = new HashMap<>();
    protected ComponentContext context;
    protected final List<PandoricalComponent> children = new ArrayList<>();

    @Override
    public void init(ComponentDef def, ComponentContext context) {
        this.id = def.id();
        this.x = def.x();
        this.y = def.y();
        this.width = def.width();
        this.height = def.height();
        this.props.putAll(def.props());
        this.context = context;
    }

    @Override
    public void updateProps(Map<String, String> changedProps) {
        this.props.putAll(changedProps);
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
