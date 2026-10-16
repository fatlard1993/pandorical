package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.camera.CameraManager;
import justfatlard.pandorical.client.capture.ViewCaptures;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
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
        if (view == null) return;
        setRotation(view.yRot(), view.xRot());
        setPosition(view.eye());
    }

    @Inject(method = "calculateFov", at = @At("HEAD"), cancellable = true)
    private void pandorical$viewFov(float partialTicks, CallbackInfoReturnable<Float> cir) {
        if (ViewCaptures.drawing() != null) cir.setReturnValue(ViewCaptures.FOV);
    }
}
