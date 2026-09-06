package justfatlard.pandorical.client.animation;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import justfatlard.pandorical.Pandorical;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Vector3f;

/**
 * Animations a mod shipped, in a form the game can play.
 *
 * <p>Loaded as ordinary client resources from {@code assets/<namespace>/animations/<name>.json},
 * which means a server mod ships one alongside its models and Pandorical's asset sync carries it
 * over with everything else - there is no separate channel for animation and nothing to register
 * in code.
 *
 * <p>The file is vanilla's own animation structure written out: a length, whether it loops, and
 * for each named bone a list of channels. Bones are named, not indexed, so an animation written
 * for {@code body} and {@code head} plays on anything whose model spells its parts that way and
 * silently skips the parts of one that does not - which is what lets a single "lie down" be
 * written once for every four-legged animal in the game.
 *
 * <pre>{@code
 * {
 *   "length": 1.0,
 *   "looping": true,
 *   "bones": {
 *     "body": [
 *       { "target": "rotation",
 *         "keyframes": [ { "time": 0.0, "value": [0, 0, 0] },
 *                        { "time": 1.0, "value": [-90, 0, 0] } ] }
 *     ]
 *   }
 * }
 * }</pre>
 */
public final class AnimationLibrary implements SimpleSynchronousResourceReloadListener {

	private static final String DIRECTORY = "animations";
	private static final String SUFFIX = ".json";
	private static final Gson GSON = new Gson();

	/** Rotations are written in degrees, because that is how anybody thinks about an angle. */
	private static final float DEGREES_TO_RADIANS = (float) (Math.PI / 180.0);

	private static volatile Map<Identifier, AnimationDefinition> animations = Map.of();

	public static AnimationDefinition get(Identifier id) {
		return animations.get(id);
	}

	@Override
	public Identifier getFabricId() {
		return Identifier.fromNamespaceAndPath("pandorical", DIRECTORY);
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		Map<Identifier, AnimationDefinition> loaded = new HashMap<>();

		for (Map.Entry<Identifier, Resource> entry
				: manager.listResources(DIRECTORY, id -> id.getPath().endsWith(SUFFIX)).entrySet()) {
			Identifier file = entry.getKey();
			Identifier id = nameOf(file);
			if (id == null) continue;

			try (BufferedReader reader = entry.getValue().openAsReader()) {
				AnimationDefinition definition = parse(GSON.fromJson(reader, JsonObject.class));
				if (definition != null) loaded.put(id, definition);
			} catch (Exception e) {
				// One bad animation is not worth losing the rest of them over.
				Pandorical.LOGGER.warn("Could not read animation {}", file, e);
			}
		}

		animations = Map.copyOf(loaded);
		Pandorical.LOGGER.info("Loaded {} animations", animations.size());
	}

	/** Turns {@code ns:animations/name.json} back into {@code ns:name}. */
	private static Identifier nameOf(Identifier file) {
		String path = file.getPath();
		int start = DIRECTORY.length() + 1;
		if (path.length() <= start + SUFFIX.length()) return null;

		return Identifier.fromNamespaceAndPath(
			file.getNamespace(), path.substring(start, path.length() - SUFFIX.length()));
	}

	private static AnimationDefinition parse(JsonObject json) {
		if (json == null || !json.has("bones")) return null;

		float length = json.has("length") ? json.get("length").getAsFloat() : 1.0F;
		boolean looping = json.has("looping") && json.get("looping").getAsBoolean();

		Map<String, List<AnimationChannel>> bones = new HashMap<>();
		for (Map.Entry<String, com.google.gson.JsonElement> bone : json.getAsJsonObject("bones").entrySet()) {
			List<AnimationChannel> channels = new ArrayList<>();
			for (var element : bone.getValue().getAsJsonArray()) {
				AnimationChannel channel = parseChannel(element.getAsJsonObject());
				if (channel != null) channels.add(channel);
			}
			if (!channels.isEmpty()) bones.put(bone.getKey(), channels);
		}

		return bones.isEmpty() ? null : new AnimationDefinition(length, looping, bones);
	}

	private static AnimationChannel parseChannel(JsonObject json) {
		String targetName = json.has("target") ? json.get("target").getAsString() : "rotation";
		AnimationChannel.Target target = switch (targetName) {
			case "position" -> AnimationChannel.Targets.POSITION;
			case "scale" -> AnimationChannel.Targets.SCALE;
			default -> AnimationChannel.Targets.ROTATION;
		};
		boolean rotation = target == AnimationChannel.Targets.ROTATION;

		JsonArray frames = json.getAsJsonArray("keyframes");
		if (frames == null || frames.isEmpty()) return null;

		Keyframe[] keyframes = new Keyframe[frames.size()];
		for (int i = 0; i < frames.size(); i++) {
			JsonObject frame = frames.get(i).getAsJsonObject();
			JsonArray value = frame.getAsJsonArray("value");

			float x = value.get(0).getAsFloat();
			float y = value.get(1).getAsFloat();
			float z = value.get(2).getAsFloat();
			if (rotation) {
				x *= DEGREES_TO_RADIANS;
				y *= DEGREES_TO_RADIANS;
				z *= DEGREES_TO_RADIANS;
			}

			boolean smooth = !"linear".equals(
				frame.has("interpolation") ? frame.get("interpolation").getAsString() : "linear");

			keyframes[i] = new Keyframe(frame.get("time").getAsFloat(), new Vector3f(x, y, z),
				smooth ? AnimationChannel.Interpolations.CATMULLROM
					: AnimationChannel.Interpolations.LINEAR);
		}

		return new AnimationChannel(target, keyframes);
	}
}
