package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.renderer.ChestOverlayHolder;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ChestRenderState.class)
public class ChestRenderStateMixin implements ChestOverlayHolder {
	@Unique
	private Identifier pandorical$chestOverlay;

	@Override
	public void pandorical$setChestOverlay(Identifier texture) {
		this.pandorical$chestOverlay = texture;
	}

	@Override
	public Identifier pandorical$getChestOverlay() {
		return this.pandorical$chestOverlay;
	}
}
