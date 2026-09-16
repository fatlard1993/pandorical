package justfatlard.pandorical.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import justfatlard.pandorical.client.maprelief.ClientMapReliefs;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A framed map with a relief is drawn as terrain where vanilla would draw it flat. */
@Mixin(ItemFrameRenderer.class)
public class ItemFrameReliefMixin {
    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/decoration/ItemFrame;Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;F)V",
        at = @At("TAIL"))
    private void pandorical$extractRelief(ItemFrame frame, ItemFrameRenderState state, float partialTicks, CallbackInfo ci) {
        ((ClientMapReliefs.Holder) state).pandorical$setRelief(ClientMapReliefs.forFrame(frame, state.mapId));
    }

    @WrapOperation(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/MapRenderer;render(Lnet/minecraft/client/renderer/state/MapRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ZI)V"))
    private void pandorical$drawRelief(MapRenderer renderer, MapRenderState map, PoseStack poseStack,
            SubmitNodeCollector collector, boolean showOnlyFrame, int light, Operation<Void> original,
            @Local(argsOnly = true) ItemFrameRenderState state) {
        ClientMapReliefs.Drawn relief = ((ClientMapReliefs.Holder) state).pandorical$relief();
        if (relief == null) original.call(renderer, map, poseStack, collector, showOnlyFrame, light);
        else ClientMapReliefs.draw(relief, map, poseStack, collector, showOnlyFrame, light, state.distanceToCameraSq);
    }
}
