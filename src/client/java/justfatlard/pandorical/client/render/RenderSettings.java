package justfatlard.pandorical.client.render;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import justfatlard.pandorical.Pandorical;
import net.fabricmc.loader.api.FabricLoader;

/**
 * What this player has asked Pandorical to do with rendering, kept in
 * {@code config/pandorical-render.json}.
 *
 * <p>Everything here defaults to off. A platform mod that quietly changed how the game looks the
 * moment it was installed would be a poor guest, so the answer for somebody who only has Pandorical
 * is "nothing, unless you say so". A server whose own content needs it can still turn it on for the
 * duration of the visit - see {@code RenderApi}.
 */
public final class RenderSettings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = "pandorical-render.json";

	/** Stop drawing leaf faces buried inside a canopy. Off unless asked for. */
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
				// Written out on first run so the option is discoverable in the file rather than
				// only in a readme nobody opens.
				Files.writeString(path, GSON.toJson(instance));
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
