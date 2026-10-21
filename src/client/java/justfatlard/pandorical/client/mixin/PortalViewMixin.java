package justfatlard.pandorical.client.mixin;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import justfatlard.pandorical.client.portal.PortalViews;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The far side of a portal is drawn before the level's own frame begins: they share one feature dispatcher. */
@Mixin(LevelRenderer.class)
public abstract class PortalViewMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void pandorical$drawFarSide(GraphicsResourceAllocator allocator, boolean renderOutline, CameraRenderState camera,
			GpuBufferSlice terrainFog, Vector4f fogColor, boolean shouldRenderSky, boolean consistentDepthRequired, CallbackInfo ci) {
		PortalViews.drawFarSide(camera, terrainFog);
	}
}
