package justfatlard.pandorical.client.animation;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * What each entity plays, by network id, and animations baked per model root. The baked cache is
 * weak: a resource reload replaces every model.
 */
public final class EntityAnimations {
	private EntityAnimations() {}

	/** @param startedAt client wall-clock millis */
	public record Active(Identifier animation, boolean looping, long startedAt) {}

	private static final Map<Integer, Active> PLAYING = new ConcurrentHashMap<>();

	private static final Map<ModelPart, Map<Identifier, KeyframeAnimation>> BAKED =
		Collections.synchronizedMap(new WeakHashMap<>());

	public static void play(int entityId, Identifier animation, boolean looping) {
		PLAYING.put(entityId, new Active(animation, looping, System.currentTimeMillis()));
	}

	public static void stop(int entityId) {
		PLAYING.remove(entityId);
	}

	public static Active playing(int entityId) {
		return PLAYING.get(entityId);
	}

	public static void clearAll() {
		PLAYING.clear();
		BAKED.clear();
	}

	/**
	 * Null for an unknown animation. Bones the model lacks are dropped before baking: vanilla's
	 * baker throws on them, inside the renderer.
	 */
	public static KeyframeAnimation baked(ModelPart root, Identifier animation) {
		Map<Identifier, KeyframeAnimation> forModel =
			BAKED.computeIfAbsent(root, key -> new HashMap<>());

		synchronized (forModel) {
			if (forModel.containsKey(animation)) return forModel.get(animation);

			AnimationDefinition definition = AnimationLibrary.get(animation);
			KeyframeAnimation baked = definition == null ? null : onlyKnownBones(definition, root).bake(root);
			forModel.put(animation, baked);
			return baked;
		}
	}

	private static AnimationDefinition onlyKnownBones(AnimationDefinition definition, ModelPart root) {
		Function<String, ModelPart> lookup = root.createPartLookup();
		Map<String, List<AnimationChannel>> known = new HashMap<>();
		for (var bone : definition.boneAnimations().entrySet()) {
			ModelPart part;
			try {
				part = lookup.apply(bone.getKey());
			} catch (RuntimeException e) {
				part = null;
			}
			if (part != null) known.put(bone.getKey(), bone.getValue());
		}
		if (known.size() == definition.boneAnimations().size()) return definition;
		return new AnimationDefinition(definition.lengthInSeconds(), definition.looping(), known);
	}
}
