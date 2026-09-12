package justfatlard.pandorical.api;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;

/**
 * Per-player settings, shown on one screen with a section per mod: from the options menu on a
 * Pandorical client, or {@code /pandorical settings} on any.
 */
public interface SettingsApi {

    /** Call once, at init. */
    Group group(String modId, String modName);

    /**
     * Settings with one value for the whole server, shown to ops alone. Unbacked values are kept
     * by Pandorical on the overworld. Keys must not repeat a key of the mod's per-player group.
     */
    Group serverGroup(String modId, String modName);

    /** For a player with a Pandorical client. */
    void open(ServerPlayer player);

    interface Group {
        Setting<Boolean> toggle(String key, String label, boolean fallback);

        /** Options in the order given, each with the label shown for it. */
        Setting<String> choice(String key, String label, Map<String, String> options, String fallback);

        Setting<Integer> number(String key, String label, int min, int max, int step, int fallback);

        /**
         * The player's own entries, each with a button that removes it; adding is the mod's.
         * {@code entries} maps id to shown name, and {@code remove} is told the id pressed.
         * {@code set(player, id)} removes the same way; {@code get} is the ids, joined.
         */
        Setting<String> list(String key, String label, Function<ServerPlayer, Map<String, String>> entries,
                BiConsumer<ServerPlayer, String> remove);
    }

    interface Setting<T> {
        T get(ServerPlayer player);

        /** Stores the value and tells the listeners, the same as a change from the screen. */
        void set(ServerPlayer player, T value);

        Setting<T> onChange(BiConsumer<ServerPlayer, T> listener);

        /** A line under the label. */
        Setting<T> describe(String description);

        /** Keep the value in the mod's own store rather than Pandorical's. */
        Setting<T> backedBy(Function<ServerPlayer, T> getter, BiConsumer<ServerPlayer, T> setter);

        /**
         * Show the setting only while this holds for the player looking; several conditions must
         * all hold. A hidden setting is not counted, not listed, and refused from the command.
         */
        Setting<T> shownWhen(Predicate<ServerPlayer> condition);

        /** Show the setting only when another mod is installed on the server. */
        Setting<T> shownWith(String modId);

        /** Show the setting only while another setting, declared before it, has this value. */
        <V> Setting<T> shownWhen(Setting<V> other, V value);
    }
}
