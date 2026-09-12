package justfatlard.pandorical.client.renderer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public interface ChestOverlayHolder {
	void pandorical$setChestOverlay(Identifier texture);

	Identifier pandorical$getChestOverlay();
}
