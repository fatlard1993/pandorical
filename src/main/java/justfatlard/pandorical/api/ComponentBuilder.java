package justfatlard.pandorical.api;

import justfatlard.pandorical.protocol.ComponentDef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ComponentBuilder {
    private final String id;
    private final String type;
    private int x, y, width, height;
    private final Map<String, String> props = new HashMap<>();
    private final List<ComponentDef> children = new ArrayList<>();

    public ComponentBuilder(String id, String type) {
        this.id = id;
        this.type = type;
    }

    public ComponentBuilder pos(int x, int y) {
        this.x = x;
        this.y = y;
        return this;
    }

    public ComponentBuilder size(int width, int height) {
        this.width = width;
        this.height = height;
        return this;
    }

    public ComponentBuilder bounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        return this;
    }

    public ComponentBuilder prop(String key, String value) {
        this.props.put(key, value);
        return this;
    }

    public ComponentBuilder prop(String key, int value) {
        return prop(key, String.valueOf(value));
    }

    public ComponentBuilder prop(String key, long value) {
        return prop(key, String.valueOf(value));
    }

    public ComponentBuilder prop(String key, float value) {
        return prop(key, String.valueOf(value));
    }

    public ComponentBuilder prop(String key, double value) {
        return prop(key, String.valueOf(value));
    }

    public ComponentBuilder prop(String key, boolean value) {
        return prop(key, String.valueOf(value));
    }

    public ComponentBuilder scale(float scale) {
        this.props.put(ComponentType.PROP_SCALE, String.valueOf(scale));
        return this;
    }

    public ComponentBuilder rotation(float degrees) {
        this.props.put(ComponentType.PROP_ROTATION, String.valueOf(degrees));
        return this;
    }

    public ComponentBuilder props(Map<String, String> props) {
        this.props.putAll(props);
        return this;
    }

    public ComponentBuilder child(ComponentDef child) {
        this.children.add(child);
        return this;
    }

    public ComponentBuilder child(ComponentBuilder childBuilder) {
        this.children.add(childBuilder.build());
        return this;
    }

    public ComponentDef build() {
        return new ComponentDef(id, type, x, y, width, height, Map.copyOf(props), List.copyOf(children));
    }
}
