package justfatlard.pandorical.api;

import justfatlard.pandorical.protocol.ComponentUpdate;

import java.util.HashMap;
import java.util.Map;

public class ComponentUpdateBuilder {
    private final String componentId;
    private final Map<String, String> props = new HashMap<>();

    public ComponentUpdateBuilder(String componentId) {
        this.componentId = componentId;
    }

    public ComponentUpdateBuilder pos(int x, int y) {
        props.put(ComponentType.PROP_X, String.valueOf(x));
        props.put(ComponentType.PROP_Y, String.valueOf(y));
        return this;
    }

    public ComponentUpdateBuilder size(int width, int height) {
        props.put(ComponentType.PROP_WIDTH, String.valueOf(width));
        props.put(ComponentType.PROP_HEIGHT, String.valueOf(height));
        return this;
    }

    public ComponentUpdateBuilder bounds(int x, int y, int width, int height) {
        return pos(x, y).size(width, height);
    }

    public ComponentUpdateBuilder scale(float scale) {
        props.put(ComponentType.PROP_SCALE, String.valueOf(scale));
        return this;
    }

    public ComponentUpdateBuilder rotation(float degrees) {
        props.put(ComponentType.PROP_ROTATION, String.valueOf(degrees));
        return this;
    }

    public ComponentUpdateBuilder prop(String key, String value) {
        props.put(key, value);
        return this;
    }

    /**
     * A colour, written the way the client reads one. {@code prop(key, 0xFFFF0000)} would send
     * "-65536", which is read as hex and comes out as some other colour without a word said.
     *
     * @param argb 0xAARRGGBB; an alpha of 0 draws nothing, so opaque is 0xFF000000 upwards
     */
    public ComponentUpdateBuilder color(String key, int argb) {
        return prop(key, String.format("#%08X", argb));
    }

    /** Not for colours: see {@link #color}, which the client can read back as one. */
    public ComponentUpdateBuilder prop(String key, int value) {
        return prop(key, String.valueOf(value));
    }

    public ComponentUpdateBuilder prop(String key, long value) {
        return prop(key, String.valueOf(value));
    }

    public ComponentUpdateBuilder prop(String key, float value) {
        return prop(key, String.valueOf(value));
    }

    public ComponentUpdateBuilder prop(String key, double value) {
        return prop(key, String.valueOf(value));
    }

    public ComponentUpdateBuilder prop(String key, boolean value) {
        return prop(key, String.valueOf(value));
    }

    public ComponentUpdateBuilder props(Map<String, String> props) {
        this.props.putAll(props);
        return this;
    }

    public ComponentUpdate build() {
        return new ComponentUpdate(componentId, Map.copyOf(props));
    }
}
