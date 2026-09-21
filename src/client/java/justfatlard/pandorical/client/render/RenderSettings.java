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

	private static RenderSettings instance = null;

	private static RenderSettings get() {
		if (instance != null) return instance;

		Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
		try {
			if (Files.exists(path)) {
				instance = GSON.fromJson(Files.readString(path), RenderSettings.class);
			}
			if (instance == null) {
				instance = new RenderSettings();
				justfatlard.pandorical.ConfigFiles.write(path, GSON.toJson(instance));
			}
		} catch (Exception e) {
			Pandorical.LOGGER.warn("Could not read {} - using defaults", FILE_NAME, e);
			instance = new RenderSettings();
		}
		return instance;
	}

	public static boolean cullLeaves() {
		return get().cullLeaves;
	}
}
