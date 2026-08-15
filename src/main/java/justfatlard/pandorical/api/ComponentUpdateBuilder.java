package justfatlard.pandorical.api;

import justfatlard.pandorical.protocol.ComponentUpdate;

import java.util.HashMap;
import java.util.Map;

/**
 * Fluent builder for a single {@link ComponentUpdate}, including the geometry keys
 * ({@link ComponentType#PROP_X}/{@code PROP_Y}/{@code PROP_WIDTH}/{@code PROP_HEIGHT}) and
 * {@link ComponentType#PROP_SCALE}/{@code PROP_ROTATION} that {@code AbstractComponent} on the
 * client recognizes and smoothly interpolates towards. These ride the same
 * {@code Map<String,String>} the rest of the prop-update wire format already uses (no new
 * packet fields), so any prop, including geometry, can be set through {@link #prop}/{@link #props}
 * as well; these convenience methods exist purely for readability at call sites.
 */
public class ComponentUpdateBuilder {
    private final String componentId;
    private final Map<String, String> props = new HashMap<>();

    public ComponentUpdateBuilder(String componentId) {
        this.componentId = componentId;
    }

    /** Move the component to an absolute new position. Client interpolates towards it smoothly. */
    public ComponentUpdateBuilder pos(int x, int y) {
        props.put(ComponentType.PROP_X, String.valueOf(x));
        props.put(ComponentType.PROP_Y, String.valueOf(y));
        return this;
    }

    /** Resize the component to an absolute new size. Client interpolates towards it smoothly. */
    public ComponentUpdateBuilder size(int width, int height) {
        props.put(ComponentType.PROP_WIDTH, String.valueOf(width));
        props.put(ComponentType.PROP_HEIGHT, String.valueOf(height));
        return this;
    }

    public ComponentUpdateBuilder bounds(int x, int y, int width, int height) {
        return pos(x, y).size(width, height);
    }

    /** Supported on: sprite, text. See {@link ComponentType#PROP_SCALE}. */
    public ComponentUpdateBuilder scale(float scale) {
        props.put(ComponentType.PROP_SCALE, String.valueOf(scale));
        return this;
    }

    /** Supported on: sprite, text. See {@link ComponentType#PROP_ROTATION}. */
    public ComponentUpdateBuilder rotation(float degrees) {
        props.put(ComponentType.PROP_ROTATION, String.valueOf(degrees));
        return this;
    }

    public ComponentUpdateBuilder prop(String key, String value) {
        props.put(key, value);
        return this;
    }

    public ComponentUpdateBuilder props(Map<String, String> props) {
        this.props.putAll(props);
        return this;
    }

    public ComponentUpdate build() {
        return new ComponentUpdate(componentId, Map.copyOf(props));
    }
}
