package justfatlard.pandorical.client.settings;

/** The container habits switch, in the client's config so it holds across servers. */
public final class ContainerHabits {
    private ContainerHabits() {}

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
            .toggle(KEY, "Drag moves items",
                "Drag with an empty hand to move every stack you cross: left moves each stack, right one item from each",
                ContainerHabits::enabled, ContainerHabits::set);
    }

    private static boolean load() {
        return ClientPrefs.getBoolean(KEY, true);
    }

    private static void save() {
        ClientPrefs.set(KEY, enabled);
    }
}
