package justfatlard.pandorical.api;

import net.minecraft.world.entity.Entity;

/**
 * API for server mods to play animations on entities.
 *
 * <p>The animation itself is data: a mod ships {@code assets/<namespace>/animations/<name>.json}
 * and Pandorical syncs it with the rest of that mod's assets, so nothing here carries geometry or
 * keyframes. This is only the switch - who is playing what.
 *
 * <p>An animation names the bones it moves, and the bones of a vanilla model are named by vanilla,
 * so the same animation can be written once and played on anything whose model has parts by those
 * names. A "lie down" that turns {@code body} and folds {@code right_hind_leg} works on every
 * four-legged animal that spells its parts the usual way, and quietly does nothing to the parts of
 * one that does not.
 *
 * <p>Applied on top of whatever the entity was already doing rather than instead of it, so a walking
 * animal keeps walking and an animation that only touches the head leaves the legs alone.
 */
public interface AnimationApi {

	/**
	 * Start an animation on an entity, for everyone who can see it.
	 *
	 * @param animationId {@code namespace:name}, matching the shipped asset
	 * @param looping     whether it repeats, or plays once and holds its final pose
	 */
	void play(Entity entity, String animationId, boolean looping);

	/** Stop whatever this entity is playing and let its own animation have it back. */
	void stop(Entity entity);
}
