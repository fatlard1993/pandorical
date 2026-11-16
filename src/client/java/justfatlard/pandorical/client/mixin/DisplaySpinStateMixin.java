package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.renderer.DisplaySpinHolder;
import net.minecraft.client.renderer.entity.state.DisplayEntityRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(DisplayEntityRenderState.class)
public abstract class DisplaySpinStateMixin implements DisplaySpinHolder {
	@Unique
	private Matrix4f pandorical$spin;

	@Override
	public void pandorical$setSpin(Matrix4f spin) {
		this.pandorical$spin = spin;
	}

	@Override
	public Matrix4f pandorical$getSpin() {
		return this.pandorical$spin;
	}
}
