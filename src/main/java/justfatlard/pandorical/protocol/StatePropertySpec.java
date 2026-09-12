package justfatlard.pandorical.protocol;

import justfatlard.pandorical.Pandorical;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Locale;
import java.util.StringJoiner;

/**
 * One block state property as {@link SyncContentS2C.BlockEntry#stateProperties()} carries it: the
 * server writes it with {@link #encode}, the client reads it back with {@link #parse}.
 *
 * <p>Wire format: "name:type:valuesOrCount" where type is b=boolean, i=integer, e=enum
 * (comma-separated names); integers use "name:i:min:max"; legacy form is "name:valueCount".
 *
 * @param name        the property's name
 * @param type        "b", "i" or "e"; "i" when the spec names none
 * @param valueCount  how many values the property has, or -1 when the spec does not say
 * @param intMin      an integer property's lowest value, 0 unless the spec gives a range
 * @param enumValues  an enum property's comma-separated value names, otherwise null
 */
public record StatePropertySpec(String name, String type, int valueCount, int intMin, String enumValues) {

    public static String encode(Property<?> prop) {
        // Anything that is not a boolean or an integer range is described by its value
        // names, not by a count. An EnumProperty is the usual case, but a mod can define a
        // Property of its own whose values read as words - and sending "i:101" for one of
        // those would hand the client a property numbered 0..100 with the names thrown
        // away, which is exactly the shape the client's NamedIntegerProperty exists to be
        // rebuilt into. The names are the only part the blockstate JSON can match on.
        String type = "e";
        if (prop instanceof BooleanProperty) type = "b";
        else if (prop instanceof IntegerProperty) type = "i";
        // For enums, send the value names so the client can create matching properties
        if ("e".equals(type)) {
            var names = new StringJoiner(",");
            try {
                var getNameMethod = Property.class.getMethod("getName", Comparable.class);
                for (var v : prop.getPossibleValues()) {
                    names.add((String) getNameMethod.invoke(prop, v));
                }
            } catch (Exception ex) {
                for (var v : prop.getPossibleValues()) {
                    names.add(v.toString().toLowerCase(Locale.ROOT));
                }
            }
            return prop.getName() + ":" + type + ":" + names.toString();
        } else if ("i".equals(type) && prop instanceof IntegerProperty intProp) {
            // Send min:max, not just a count: flower_amount [1,4] as "flower_amount:i:4"
            // would create [0,3] on the client and fail to decode the value 4.
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
