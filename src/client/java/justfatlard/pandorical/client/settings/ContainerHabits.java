package justfatlard.pandorical.client.settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The switch for the container habits (wheel to move a stack, drag to move a row), kept in the
 * client's own config so it survives whichever server the player is on.
 */
public final class ContainerHabits {
    private ContainerHabits() {}

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("pandorical-client.properties");
    private static final String KEY = "container_habits";
    private static Boolean enabled;

    public static boolean enabled() {
        if (enabled == null) enabled = load();
        return enabled;
    }

    public static void set(boolean on) {
        enabled = on;
        save();
    }

    /** On Pandorical's own page of the settings, so a player who hates it can say so once. */
    public static void register() {
        ClientSettings.INSTANCE.group("pandorical", "Pandorical")
            .toggle(KEY, "Wheel and drag move items",
                "Scroll over a stack to move one item across; drag with an empty hand to move every stack you cross",
                ContainerHabits::enabled, ContainerHabits::set);
    }

    private static boolean load() {
        Properties props = new Properties();
        try {
            if (Files.exists(FILE)) props.load(Files.newBufferedReader(FILE));
        } catch (IOException ignored) {
            // A config that cannot be read is the default, not a crash.
        }
        return Boolean.parseBoolean(props.getProperty(KEY, "true"));
    }

    private static void save() {
        Properties props = new Properties();
        try {
            if (Files.exists(FILE)) props.load(Files.newBufferedReader(FILE));
            props.setProperty(KEY, Boolean.toString(enabled));
            Files.createDirectories(FILE.getParent());
            props.store(Files.newBufferedWriter(FILE), "Pandorical client preferences");
        } catch (IOException ignored) {
            // Nowhere to write it: the choice holds for the session.
        }
    }
}
