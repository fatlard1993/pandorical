package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.renderer.OverlayRenderState;
import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(MinecartRenderState.class)
public abstract class MinecartRenderStateMixin implements OverlayRenderState {
	@Unique
	private Identifier pandorical$overlay;

	@Override
	public Identifier pandorical$overlay() {
		return pandorical$overlay;
	}

	@Override
	public void pandorical$setOverlay(Identifier texture) {
		this.pandorical$overlay = texture;
	}
}
