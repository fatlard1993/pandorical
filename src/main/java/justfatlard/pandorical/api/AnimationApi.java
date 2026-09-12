package justfatlard.pandorical.api;

import net.minecraft.world.entity.Entity;

/**
 * Animations played on entities.
 *
 * <p>An animation is an asset, {@code assets/<namespace>/animations/<name>.json}, synced with the
 * rest of the mod's assets. It moves bones by the model part names vanilla gives them, and a part
 * the entity's model lacks is skipped without error.
 *
 * <p>Layered over the entity's own animation: parts the animation does not touch keep moving.
 */
public interface AnimationApi {

	/**
	 * For everyone who can see the entity.
	 *
	 * @param animationId {@code namespace:name}, matching the shipped asset
	 * @param looping     whether it repeats, or plays once and holds its final pose
	 */
	void play(Entity entity, String animationId, boolean looping);

	/** Hand the entity back to its own animation. */
	void stop(Entity entity);
}
