package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.renderer.OverlayTextureHolder;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Merges {@link OverlayTextureHolder} onto every render state so the overlay
 * texture can travel from extraction (entity in hand) to layer submit (state
 * only). The field is rewritten on every extraction by
 * {@link LivingEntityRendererMixin}, so reuse of pooled states across entities
 * cannot leak an overlay.
 */
@Mixin(EntityRenderState.class)
public class EntityRenderStateMixin implements OverlayTextureHolder {
	@Unique
	private Identifier pandorical$overlayTexture;

	@Override
	public void pandorical$setOverlayTexture(Identifier texture) {
		this.pandorical$overlayTexture = texture;
	}

	@Override
	public Identifier pandorical$getOverlayTexture() {
		return this.pandorical$overlayTexture;
	}
}
