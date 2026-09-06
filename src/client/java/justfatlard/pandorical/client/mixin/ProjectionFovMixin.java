package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.camera.CameraManager;
import net.minecraft.client.renderer.Projection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Narrows the field of view when the server has asked for a closer look.
 *
 * <p>Applied to the projection rather than to the player's own options, so nothing the player set
 * is touched and nothing has to be put back: releasing the zoom is the server sending 1.0, and a
 * disconnect mid-zoom leaves no trace in anybody's settings.
 *
 * <p>The field of view is the third parameter of {@code setupPerspective(zNear, zFar, fov, width,
 * height)}, which is local slot three with {@code this} in slot zero. Worth naming, because the
 * signature is five floats and a wrong slot would silently scale the far plane instead - a world
 * that fades out a few blocks away rather than one that does not zoom.
 */
@Mixin(Projection.class)
public class ProjectionFovMixin {

	@ModifyVariable(method = "setupPerspective", at = @At("HEAD"), argsOnly = true, index = 3)
	private float pandorical$narrowFov(float fov) {
		float factor = CameraManager.getZoomFactor();
		return factor == 1.0F ? fov : fov * factor;
	}
}
