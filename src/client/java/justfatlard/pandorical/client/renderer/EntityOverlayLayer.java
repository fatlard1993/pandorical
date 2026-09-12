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

@Environment(EnvType.CLIENT)
public class EntityOverlayLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>>
		extends RenderLayer<S, M> {

	// The submit order vanilla body-cover layers such as sheep wool use.
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
