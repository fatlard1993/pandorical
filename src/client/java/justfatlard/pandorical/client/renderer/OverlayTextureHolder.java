package justfatlard.pandorical.client.renderer;

import net.minecraft.resources.Identifier;

/**
 * Duck interface merged onto {@code EntityRenderState} by
 * {@code EntityRenderStateMixin}, carrying the per-entity overlay texture from
 * extraction time (where the entity is available) to layer submit time (where
 * only the render state is). Lives outside the mixin package because Sponge
 * Mixin rejects non-mixin classes inside a mixin-owned package at class load.
 *
 * <p>Render states are reused across entities, so the extraction hook writes
 * this field on every extraction (null included); it never goes stale.
 */
public interface OverlayTextureHolder {
	void pandorical$setOverlayTexture(Identifier texture);

	Identifier pandorical$getOverlayTexture();
}
