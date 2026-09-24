package justfatlard.pandorical.changelog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.brief.Brief;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What a mod says about itself, shipped as a file rather than written in code.
 *
 * <p>A mod drops {@code pandorical.changelog.json} in its resources and needs no code at all - no
 * dependency, no initialiser, nothing to keep in step. Which matters because these are prose, and
 * prose that lives in a Java string literal gets edited about as often as Java does.
 *
 * <pre>{@code
 * {
 *   "overview": ["Amethyst doors you can lock to everyone but the people you build with."],
 *   "versions": {
 *     "1.1.0": ["Growing a shared geode now asks everyone in the cluster."],
 *     "1.0.0": ["A door of amethyst, and a geode of your own behind it."]
 *   }
 * }
 * }</pre>
 *
 * <p>One line per change, and a line is a sentence. The reader is a player who has just logged in
 * and wants to know whether anything they care about moved; they are not reading a commit message
 * and they did not ask why. "Horses turn the way you press" is the note. Why they used to turn the
 * other way belongs in the commit that fixed it, where somebody looking for it will actually be.
 * A note past about a hundred characters is almost always a note with its reasoning still attached.
 *
 * <p>Read once, after every mod has initialised, and only where nothing was declared in code:
 * {@link justfatlard.pandorical.api.ChangelogApi} and {@link justfatlard.pandorical.api.BriefApi}
 * win, so a mod that computes a note at runtime is not overruled by a stale file beside it.
 *
 * <p>A file that will not parse is logged and skipped. One mod's bad json is not a reason for the
 * other thirty-nine to go unmentioned.
 */
final class NoteFiles {
	private NoteFiles() {}

	private static final String FILE = "pandorical.changelog.json";

	private static boolean loaded;

	/** Read every mod's file, once. */
	static synchronized void loadOnce() {
		if (loaded) return;
		loaded = true;
		for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
			Optional<Path> found = container.findPath(FILE);
			if (found.isEmpty()) continue;
			read(container.getMetadata().getId(), found.get());
		}
	}

	private static void read(String modId, Path path) {
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

			if (json.has("overview") && !Brief.INSTANCE.declared(modId)) {
				List<String> lines = lines(json.get("overview"));
				if (!lines.isEmpty()) {
					PandoricalApi.brief().overview(modId, lines.toArray(new String[0]));
				}
			}

			if (json.has("versions")) {
				for (var entry : json.getAsJsonObject("versions").entrySet()) {
					if (Changelog.INSTANCE.declared(modId, entry.getKey())) continue;
					List<String> lines = lines(entry.getValue());
					if (lines.isEmpty()) continue;
					PandoricalApi.changelog().note(modId, entry.getKey(), lines.toArray(new String[0]));
				}
			}
		} catch (Exception e) {
			Pandorical.LOGGER.warn("[pandorical] could not read {} from {}: {}",
				FILE, modId, e.toString());
		}
	}

	/** One string, or an array of them; a mod should not have to know which this wanted. */
	private static List<String> lines(JsonElement element) {
		List<String> out = new ArrayList<>();
		if (element == null || element.isJsonNull()) return out;
		if (element.isJsonArray()) {
			for (JsonElement each : element.getAsJsonArray()) {
				if (each.isJsonPrimitive()) add(out, each.getAsString());
			}
		} else if (element.isJsonPrimitive()) {
			add(out, element.getAsString());
		}
		return out;
	}

	private static void add(List<String> out, String line) {
		String trimmed = line == null ? "" : line.strip();
		if (!trimmed.isEmpty()) out.add(trimmed);
	}
}
