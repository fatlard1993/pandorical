package justfatlard.pandorical.client.api;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Settings a client-side mod puts in the mod menu.
 *
 * <p>The mirror of {@code PandoricalApi.settings()} for mods with no server half. Declare once
 * at client init; the values stay the mod's own - each setting is a getter and a setter over
 * whatever the mod already keeps - and the menu becomes another way to reach them. Call
 * {@link #changed()} when a value moves by some other road, a config file say, so the menu's
 * copy follows.
 */
public interface ClientSettingsApi {
    Group group(String modId, String modName);

    /** The client's values changed outside the menu: tell the server what they are now. */
    void changed();

    interface Group {
        Group toggle(String key, String label, String description, Supplier<Boolean> get, Consumer<Boolean> set);

        Group choice(String key, String label, String description, Map<String, String> options,
                Supplier<String> get, Consumer<String> set);

        Group number(String key, String label, String description, int min, int max, int step,
                Supplier<Integer> get, Consumer<Integer> set);
    }
}
