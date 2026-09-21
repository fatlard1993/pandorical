package justfatlard.pandorical.client.renderer;

import justfatlard.pandorical.client.animation.EntityAnimations;

public interface AnimationHolder {

	void pandorical$setAnimation(EntityAnimations.Active animation);

	EntityAnimations.Active pandorical$getAnimation();

	/**
	 * How far into the animation this frame is, in milliseconds, read once when the state was
	 * taken.
	 *
	 * <p>Carried on the state rather than read where it is used, because everything sharing a
	 * state - the entity's model, its armour, a sheep's wool - has to be posed at the same instant.
	 * Read per model, a wall clock ticks over between two of them and poses them a millisecond
	 * apart, which is a fourteenth of a model unit while a position track is moving. That is
	 * invisible on anything with clearance and not on a baby sheep, whose wool is the body model
	 * again at the same size, held off it by draw order alone: part the two and the white shell
	 * fights the body for every pixel.
	 */
	void pandorical$setAnimationElapsed(long millis);

	long pandorical$getAnimationElapsed();
}
