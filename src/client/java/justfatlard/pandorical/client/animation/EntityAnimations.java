package justfatlard.pandorical.client.animation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.Identifier;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.animation.AnimationChannel;

/**
 * Which entities are playing what, and the baked animation to play them with.
 *
 * <p>Two caches for two different lifetimes. What an entity is playing is small, changes when the
 * server says so, and is keyed by network id. A baked animation is expensive - it resolves every
 * named bone against a real model - and depends on the model rather than the entity, so it is kept
 * against the model's root part and shared by every entity that renders through it.
 *
 * <p>The baked cache holds model parts weakly. A resource reload replaces every model in the game,
 * and a strong reference here would keep the old ones alive for as long as the client ran.
 */
public final class EntityAnimations {
	private EntityAnimations() {}

	/** @param startedAt when it began, in client time, so elapsed can be worked out per frame */
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

	/** Everything stops at a disconnect; the next server has its own ideas. */
	public static void clearAll() {
		PLAYING.clear();
		BAKED.clear();
	}

	/**
	 * The animation baked against this particular model, or null if there is no such animation.
	 *
	 * <p>Baking is what binds bone names to real parts. A name the model does not have is left
	 * out before baking, which is what lets one animation be written for every animal that
	 * spells its parts the usual way: a flop written with a salmon's two body halves still plays
	 * on a cod, on the parts the cod has. The game's own baker refuses such a name outright,
	 * and a refusal inside the renderer is a crash for whoever is looking at the fish.
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

	/** The definition with every bone this model does not have dropped. */
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
