package justfatlard.pandorical.api;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;

/**
 * Per-player settings a server mod would otherwise put behind a command.
 *
 * <p>A mod declares its settings once at init, under its own name, and Pandorical shows them all
 * to the player on one screen: a section per mod, a row per setting, reached from the options
 * menu on a Pandorical client or from {@code /pandorical settings} on any. Values are kept per
 * player by Pandorical unless the mod already keeps them, in which case the setting is backed by
 * the mod's own getter and setter and the screen is simply another way to reach them.
 *
 * <pre>
 *   PandoricalApi.settings().group("block-tip", "Block Tip")
 *       .choice("mode", "Show tips", Map.of("always", "Always", "off", "Off"), "always")
 *       .backedBy(player -> ..., (player, value) -> ...);
 * </pre>
 */
public interface SettingsApi {

    /** The settings of one mod, shown together under its name. Call once, at init. */
    Group group(String modId, String modName);

    /**
     * A group whose settings are the server's, one value for everyone, shown to ops alone.
     *
     * <p>For what a mod would otherwise keep in a config file: rates, cooldowns, switches that
     * shape the whole world. {@code backedBy} points a setting at the mod's own config, with a
     * setter that writes the file, so the file stays the record and the menu is a hand on it.
     * Without a backing, the value is kept by Pandorical on the overworld. Keys must not repeat
     * a key of the mod's per-player group.
     */
    Group serverGroup(String modId, String modName);

    /** Open the settings screen for a player who has a Pandorical client. */
    void open(ServerPlayer player);

    interface Group {
        Setting<Boolean> toggle(String key, String label, boolean fallback);

        /** Options in the order given, each with the label shown for it. */
        Setting<String> choice(String key, String label, Map<String, String> options, String fallback);

        Setting<Integer> number(String key, String label, int min, int max, int step, int fallback);

        /**
         * A list of the player's own entries, each shown with a button that takes it off.
         *
         * <p>For what a mod collects by command or by play - blocks muted, places remembered -
         * where seeing the list and pruning it is the whole ask; adding stays where it was.
         * {@code entries} gives the player's entries as id to shown name, and {@code remove} is
         * told the id of the one pressed. {@code set(player, id)} removes the same way, so the
         * command can too; {@code get} is the ids, joined.
         */
        Setting<String> list(String key, String label, Function<ServerPlayer, Map<String, String>> entries,
                BiConsumer<ServerPlayer, String> remove);
    }

    interface Setting<T> {
        T get(ServerPlayer player);

        /** Stores the value and tells the listeners, the same as a change from the screen. */
        void set(ServerPlayer player, T value);

        Setting<T> onChange(BiConsumer<ServerPlayer, T> listener);

        /** A line under the label, for what the setting does when the label cannot say. */
        Setting<T> describe(String description);

        /** Keep the value in the mod's own store rather than Pandorical's. */
        Setting<T> backedBy(Function<ServerPlayer, T> getter, BiConsumer<ServerPlayer, T> setter);

        /**
         * Show the setting only while this holds for the player looking. Several conditions
         * all have to hold. A setting that is not shown is not counted, not listed, and refused
         * from the command; the screen is rebuilt when a change may have shown or hidden one.
         */
        Setting<T> shownWhen(Predicate<ServerPlayer> condition);

        /** Show the setting only when another mod is installed on the server. */
        Setting<T> shownWith(String modId);

        /** Show the setting only while another setting, declared before it, has this value. */
        <V> Setting<T> shownWhen(Setting<V> other, V value);
    }
}
