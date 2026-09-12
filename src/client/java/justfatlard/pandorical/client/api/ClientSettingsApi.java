package justfatlard.pandorical.client.api;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Mod menu settings for a client-only mod, as {@code PandoricalApi.settings()} is for a server
 * mod. Declare once at client init; each setting is a getter and setter over the mod's own value.
 */
public interface ClientSettingsApi {
    Group group(String modId, String modName);

    /** Call when a value changed outside the menu, such as from a config file. */
    void changed();

    interface Group {
        Group toggle(String key, String label, String description, Supplier<Boolean> get, Consumer<Boolean> set);

        Group choice(String key, String label, String description, Map<String, String> options,
                Supplier<String> get, Consumer<String> set);

        Group number(String key, String label, String description, int min, int max, int step,
                Supplier<Integer> get, Consumer<Integer> set);
    }
}
