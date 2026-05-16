package justfatlard.pandorical.client.content;

import net.minecraft.world.level.block.state.properties.Property;

import java.util.*;

/**
 * A property with integer values that serializes to custom string names.
 * Used to represent enum-like block state properties on the client.
 */
public final class NamedIntegerProperty extends Property<Integer> {
    private final List<Integer> values;
    private final List<String> names;
    private final Map<String, Integer> nameToValue;

    private NamedIntegerProperty(String name, List<String> valueNames) {
        super(name, Integer.class);
        var vals = new ArrayList<Integer>();
        this.names = List.copyOf(valueNames);
        this.nameToValue = new HashMap<>();
        for (int i = 0; i < valueNames.size(); i++) {
            vals.add(i);
            nameToValue.put(valueNames.get(i), i);
        }
        this.values = List.copyOf(vals);
    }

    public static NamedIntegerProperty create(String name, List<String> valueNames) {
        return new NamedIntegerProperty(name, valueNames);
    }

    @Override
    public List<Integer> getPossibleValues() {
        return values;
    }

    @Override
    public String getName(Integer value) {
        if (value >= 0 && value < names.size()) {
            return names.get(value);
        }
        return String.valueOf(value);
    }

    @Override
    public Optional<Integer> getValue(String name) {
        Integer val = nameToValue.get(name);
        if (val != null) return Optional.of(val);
        try {
            int i = Integer.parseInt(name);
            if (i >= 0 && i < values.size()) return Optional.of(i);
        } catch (NumberFormatException ignored) {}
        return Optional.empty();
    }

    @Override
    public int getInternalIndex(Integer value) {
        return value;
    }
}
