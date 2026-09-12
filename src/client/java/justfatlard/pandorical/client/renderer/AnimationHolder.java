package justfatlard.pandorical.client.renderer;

import justfatlard.pandorical.client.animation.EntityAnimations;

public interface AnimationHolder {

	void pandorical$setAnimation(EntityAnimations.Active animation);

	EntityAnimations.Active pandorical$getAnimation();
}
