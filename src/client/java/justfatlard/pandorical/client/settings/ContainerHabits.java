package justfatlard.pandorical.client.settings;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** The container habits switch, in the client's config so it holds across servers. */
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
        }
    }
}
