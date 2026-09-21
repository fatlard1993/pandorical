package justfatlard.pandorical.protocol;

import justfatlard.pandorical.Pandorical;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps what a mod hands the server inside what the wire accepts.
 *
 * <p>A codec that refuses its own value throws while encoding, and an encode failure on a
 * clientbound payload disconnects the player it was meant for. A mod with one overlong label would
 * take down every Pandorical player at join. So the server trims instead, and says once whose
 * value it trimmed: a shortened label is a blemish, a kicked player is an outage.
 */
public final class Wire {
    private Wire() {}

    private static final Set<String> SAID = ConcurrentHashMap.newKeySet();

    /** The value if it fits, else the front of it. {@code what} names it in the log. */
    public static String fit(String value, int max, String what) {
        if (value == null) return "";
        if (value.length() <= max) return value;
        say("Pandorical trimmed {} to {} characters to fit the wire: \"{}\"", what, max, preview(value));
        return value.substring(0, max);
    }

    /** The list if it fits, else its first {@code max} entries. */
    public static <T> List<T> fit(List<T> values, int max, String what) {
        if (values == null) return List.of();
        if (values.size() <= max) return values;
        say("Pandorical kept the first {} of {} {}: the rest do not fit the wire", max, values.size(), what);
        return List.copyOf(values.subList(0, max));
    }

    /** Enough of the value to recognise it, without pouring the whole of it into the log. */
    private static String preview(String value) {
        return value.length() <= 48 ? value : value.substring(0, 48) + "...";
    }

    /** Logs once per distinct message, so a per-tick send cannot flood the log. */
    private static void say(String format, Object... args) {
        StringBuilder key = new StringBuilder(format);
        for (Object arg : args) key.append('\u0000').append(arg);
        if (SAID.add(key.toString())) Pandorical.LOGGER.warn(format, args);
    }
}
