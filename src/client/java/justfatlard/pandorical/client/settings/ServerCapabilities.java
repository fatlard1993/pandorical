package justfatlard.pandorical.client.settings;

import java.util.List;
import java.util.Set;

/** What the server said it can do, for client code that adds a control only when there is something behind it. */
public final class ServerCapabilities {
    private ServerCapabilities() {}

    private static volatile Set<String> capabilities = Set.of();

    public static void set(List<String> declared) {
        capabilities = Set.copyOf(declared);
    }

    public static void clear() {
        capabilities = Set.of();
    }

    public static boolean has(String capability) {
        return capabilities.contains(capability);
    }
}
