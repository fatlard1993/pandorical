package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.camera.CameraManager;
import justfatlard.pandorical.client.capture.ViewCaptures;
import net.minecraft.client.Camera;
import justfatlard.pandorical.client.animation.ClientOrientations;
import justfatlard.pandorical.push.EntityOrientations;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow protected abstract void setRotation(float yRot, float xRot);

    @Shadow protected abstract void setPosition(Vec3 position);

    @ModifyArg(
        method = "alignWithEntity",
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

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void pandorical$viewFrom(float partialTicks, CallbackInfo ci) {
        ViewCaptures.View view = ViewCaptures.drawing();
        if (view == null) {
            pandorical$turnWithVehicle();
            return;
        }
        setRotation(view.yRot(), view.xRot());
        setPosition(view.eye());
    }

    @Shadow private Entity entity;
    @Shadow private boolean detached;
    @Shadow @Final private Quaternionf rotation;
    @Shadow @Final private Vector3f forwards;
    @Shadow @Final private Vector3f up;
    @Shadow @Final private Vector3f left;
    @Shadow private int matrixPropertiesDirty;

    /**
     * Looking out of the eyes of a rider on something turned, the view turns with it, tilted after
     * the rider's own yaw and pitch, so the mouse still looks about from wherever the seat now
     * points. The eyes themselves are already where the seating carried them.
     */
    @Unique
    private void pandorical$turnWithVehicle() {
        if (detached || entity == null || entity.getVehicle() == null || !ClientOrientations.viewTurns()) return;
        Quaternionfc turned = ClientOrientations.now(entity.getVehicle().getId());
        if (turned == null) return;
        Quaternionf tilt = EntityOrientations.tilt(turned);
        tilt.mul(rotation, rotation);
        rotation.transform(0F, 0F, -1F, forwards);
        rotation.transform(0F, 1F, 0F, up);
        rotation.transform(-1F, 0F, 0F, left);
        matrixPropertiesDirty |= 3;
    }

    @Inject(method = "calculateFov", at = @At("HEAD"), cancellable = true)
    private void pandorical$viewFov(float partialTicks, CallbackInfoReturnable<Float> cir) {
        if (ViewCaptures.drawing() != null) cir.setReturnValue(ViewCaptures.FOV);
    }
}
