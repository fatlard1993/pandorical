package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.camera.CameraManager;
import net.minecraft.client.renderer.Projection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Index 3 is {@code fov} in {@code setupPerspective(zNear, zFar, fov, width, height)}, with
 * {@code this} in slot 0. All five are floats, so a wrong index silently scales another one.
 */
@Mixin(Projection.class)
public class ProjectionFovMixin {

	@ModifyVariable(method = "setupPerspective", at = @At("HEAD"), argsOnly = true, index = 3)
	private float pandorical$narrowFov(float fov) {
		float factor = CameraManager.getZoomFactor();
		return factor == 1.0F ? fov : fov * factor;
	}
}
