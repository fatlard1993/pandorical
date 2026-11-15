package justfatlard.pandorical.client.render;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import justfatlard.pandorical.Pandorical;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public final class RenderSettings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = "pandorical-render.json";

	private boolean cullLeaves = false;

	/** Read as the class loads, which the JVM does once, whichever chunk builder asks first. */
	private static final RenderSettings INSTANCE = load();

	private static RenderSettings load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
		try {
			RenderSettings read = null;
			if (Files.exists(path)) {
				read = GSON.fromJson(Files.readString(path), RenderSettings.class);
			}
			if (read == null) {
				read = new RenderSettings();
				justfatlard.pandorical.ConfigFiles.write(path, GSON.toJson(read));
			}
			return read;
		} catch (Exception e) {
			Pandorical.LOGGER.warn("Could not read {} - using defaults", FILE_NAME, e);
			return new RenderSettings();
		}
	}

	public static boolean cullLeaves() {
		return INSTANCE.cullLeaves;
	}
}
