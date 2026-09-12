package justfatlard.pandorical.protocol;

import justfatlard.pandorical.Pandorical;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Locale;
import java.util.StringJoiner;

/**
 * Wire format "name:type:rest": {@code b} boolean, {@code i} with "min:max", {@code e} with
 * comma-separated value names. The legacy form is "name:valueCount".
 *
 * @param type        "i" when the spec names none
 * @param valueCount  -1 when the spec does not say
 * @param enumValues  null unless type is "e"
 */
public record StatePropertySpec(String name, String type, int valueCount, int intMin, String enumValues) {

    public static String encode(Property<?> prop) {
        // Anything but a boolean or an integer range goes by value names, which are all the
        // blockstate JSON can match on; a mod's own word-valued Property included.
        String type = "e";
        if (prop instanceof BooleanProperty) type = "b";
        else if (prop instanceof IntegerProperty) type = "i";
        if ("e".equals(type)) {
            var names = new StringJoiner(",");
            try {
                var getNameMethod = Property.class.getMethod("getName", Comparable.class);
                for (var v : prop.getPossibleValues()) {
                    names.add((String) getNameMethod.invoke(prop, v));
                }
            } catch (Exception ex) {
                names = new StringJoiner(",");
                for (var v : prop.getPossibleValues()) {
                    names.add(v.toString().toLowerCase(Locale.ROOT));
                }
            }
            return prop.getName() + ":" + type + ":" + names.toString();
        } else if ("i".equals(type) && prop instanceof IntegerProperty intProp) {
            // min:max, not a count: a range not starting at 0 would shift on the client.
            int min = intProp.getPossibleValues().stream().mapToInt(Integer::intValue).min().getAsInt();
            int max = intProp.getPossibleValues().stream().mapToInt(Integer::intValue).max().getAsInt();
            return prop.getName() + ":" + type + ":" + min + ":" + max;
        } else {
            return prop.getName() + ":" + type + ":" + prop.getPossibleValues().size();
        }
    }

    /** {@code blockId} only names the block in the warning a malformed count or range logs. */
    public static StatePropertySpec parse(String propSpec, String blockId) {
        // split(":",3) leaves parts[2]="min:max" for an integer range
        String[] parts = propSpec.split(":", 3);
        String propName = parts[0];
        String propType = "i";
        int valueCount = -1;
        int intMin = 0;
        String enumValues = null;
        if (parts.length == 3) {
            propType = parts[1];
            if ("e".equals(propType)) {
                enumValues = parts[2];
                valueCount = parts[2].split(",").length;
            } else if ("i".equals(propType) && parts[2].contains(":")) {
                String[] minMax = parts[2].split(":", 2);
                try {
                    intMin = Integer.parseInt(minMax[0]);
                    int intMax = Integer.parseInt(minMax[1]);
                    valueCount = intMax - intMin + 1;
                } catch (NumberFormatException ignored) {
                    Pandorical.LOGGER.warn("[pandorical] Malformed state prop range '{}' for block '{}', defaulting to 0", parts[2], blockId);
                }
            } else {
                try { valueCount = Integer.parseInt(parts[2]); } catch (NumberFormatException ignored) {
                    Pandorical.LOGGER.warn("[pandorical] Malformed state prop range '{}' for block '{}', defaulting to 0", parts[2], blockId);
                }
            }
        } else if (parts.length == 2) {
            try { valueCount = Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) {
                Pandorical.LOGGER.warn("[pandorical] Malformed state prop range '{}' for block '{}', defaulting to 0", parts[1], blockId);
            }
        }
        return new StatePropertySpec(propName, propType, valueCount, intMin, enumValues);
    }
}
