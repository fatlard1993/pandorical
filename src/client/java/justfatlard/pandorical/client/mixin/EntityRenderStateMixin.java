package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.renderer.OverlayTextureHolder;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import justfatlard.pandorical.client.animation.EntityAnimations;
import justfatlard.pandorical.client.renderer.AnimationHolder;

@Mixin(EntityRenderState.class)
public class EntityRenderStateMixin implements OverlayTextureHolder,
		AnimationHolder {

	@Unique
	private EntityAnimations.Active pandorical$animation;

	@Override
	public void pandorical$setAnimation(
			EntityAnimations.Active animation) {
		this.pandorical$animation = animation;
	}

	@Override
	public EntityAnimations.Active pandorical$getAnimation() {
		return this.pandorical$animation;
	}
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
