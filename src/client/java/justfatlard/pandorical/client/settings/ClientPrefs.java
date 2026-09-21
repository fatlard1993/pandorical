package justfatlard.pandorical.client.settings;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * The client's own preferences file, which holds across servers.
 *
 * <p>One reader and one writer for it, because two settings each opening it their own way is two
 * chances to write a half a file and two places to fix when one of them is wrong.
 */
public final class ClientPrefs {
    private ClientPrefs() {}

    public static final Path FILE =
        FabricLoader.getInstance().getConfigDir().resolve("pandorical-client.properties");

    public static String get(String key, String fallback) {
        return read().getProperty(key, fallback);
    }

    public static boolean getBoolean(String key, boolean fallback) {
        return Boolean.parseBoolean(get(key, Boolean.toString(fallback)));
    }

    public static void set(String key, String value) {
        Properties props = read();
        props.setProperty(key, value);
        try {
            justfatlard.pandorical.ConfigFiles.write(FILE,
                writer -> props.store(writer, "Pandorical client preferences"));
        } catch (IOException e) {
            justfatlard.pandorical.Pandorical.LOGGER.warn(
                "[pandorical] could not save client preferences", e);
        }
    }

    public static void set(String key, boolean value) {
        set(key, Boolean.toString(value));
    }

    private static Properties read() {
        Properties props = new Properties();
        if (!Files.exists(FILE)) return props;
        try (Reader reader = Files.newBufferedReader(FILE)) {
            props.load(reader);
        } catch (IOException e) {
            justfatlard.pandorical.Pandorical.LOGGER.warn(
                "[pandorical] could not read client preferences", e);
        }
        return props;
    }
}
