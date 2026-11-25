package justfatlard.pandorical.client.renderer;

import org.joml.Quaternionfc;
import org.jspecify.annotations.Nullable;

/** A render state's turn: the entity's own orientation, or the tilt of the vehicle it rides. */
public interface OrientationHolder {

	void pandorical$setOrientation(@Nullable Quaternionfc orientation);

	@Nullable Quaternionfc pandorical$getOrientation();
}
