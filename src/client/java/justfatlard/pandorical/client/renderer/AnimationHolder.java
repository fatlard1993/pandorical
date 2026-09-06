package justfatlard.pandorical.client.renderer;

import justfatlard.pandorical.client.animation.EntityAnimations;

/**
 * Carries the animation an entity is playing from the point where the entity is known to the point
 * where its model is posed.
 *
 * <p>Rendering happens from a render state, not from the entity: by the time a model is being
 * posed, the entity is gone. Mirrors the way the overlay texture reaches the same place.
 */
public interface AnimationHolder {

	void pandorical$setAnimation(EntityAnimations.Active animation);

	EntityAnimations.Active pandorical$getAnimation();
}
