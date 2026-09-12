package justfatlard.pandorical.client.renderer;

import net.minecraft.resources.Identifier;

/** Outside the mixin package: Mixin rejects non-mixin classes in a mixin package at class load. */
public interface OverlayTextureHolder {
	void pandorical$setOverlayTexture(Identifier texture);

	Identifier pandorical$getOverlayTexture();
}
