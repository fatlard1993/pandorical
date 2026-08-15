package justfatlard.pandorical.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * Generic overlay layer added to every {@code LivingEntityRenderer} by
 * {@code LivingEntityRendererMixin}. Re-submits the renderer's own parent
 * model with the server-pushed overlay texture; a no-op for the overwhelming
 * majority of entities, whose render state carries no overlay.
 *
 * <p>Defensive by construction: no overlay or an invisible entity submits
 * nothing, and a texture id that does not resolve renders the vanilla
 * missing-texture pattern rather than crashing (renderColoredCutoutModel goes
 * through RenderTypes.entityCutout, which falls back like any unknown
 * texture).
 */
@Environment(EnvType.CLIENT)
public class EntityOverlayLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>>
		extends RenderLayer<S, M> {

	// Same submit order vanilla body-cover layers (e.g. sheep wool) use
	private static final int SUBMIT_ORDER = 1;
	private static final int COLOR_WHITE = -1;

	public EntityOverlayLayer(RenderLayerParent<S, M> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, S state,
			float yRot, float xRot) {
		if (!(state instanceof OverlayTextureHolder holder)) return;
		Identifier texture = holder.pandorical$getOverlayTexture();
		if (texture == null || state.isInvisible) return;

		renderColoredCutoutModel(getParentModel(), texture, poseStack, collector, light, state,
			COLOR_WHITE, SUBMIT_ORDER);
	}
}
