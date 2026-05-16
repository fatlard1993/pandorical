package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.camera.CameraManager;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Mixin to override camera distance based on server-sent camera hints.
 * Modifies the argument to getMaxZoom() which controls third-person camera distance.
 */
@Mixin(Camera.class)
public class CameraMixin {

    @ModifyArg(
        method = "update",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"),
        index = 0
    )
    private float pandorical$modifyCameraDistance(float originalDistance) {
        float override = CameraManager.getOverrideDistance();
        if (override > 0) {
            return override;
        }
        return originalDistance;
    }
}
