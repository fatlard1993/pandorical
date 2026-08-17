package justfatlard.pandorical.client.renderer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;

/**
 * Carries a chest's overlay texture from render state extraction, which knows
 * the block position, to submit, which only has the state. Mixed onto
 * ChestRenderState.
 */
@Environment(EnvType.CLIENT)
public interface ChestOverlayHolder {
	void pandorical$setChestOverlay(Identifier texture);

	Identifier pandorical$getChestOverlay();
}
