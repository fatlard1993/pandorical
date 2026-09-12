package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.render.LeafCulling;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla culls leaf-against-leaf faces only on Fast graphics ({@code cutoutLeaves} in
 * {@code LeavesBlock.skipRendering}). Hooked here rather than there because
 * {@code skipRendering} gets no position, so it cannot tell a buried leaf from the canopy's shell.
 */
@Mixin(ModelBlockRenderer.class)
public abstract class LeafCullingMixin {

	/** RETURN, and only on true, so faces vanilla already culled cost nothing. */
	@Inject(method = "shouldRenderFace", at = @At("RETURN"), cancellable = true)
	private void pandorical$cullBuriedLeaves(BlockAndTintGetter level, BlockState state,
			Direction direction, BlockPos neighbourPos, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ()) return;
		if (LeafCulling.isBuriedLeafFace(level, state, neighbourPos)) cir.setReturnValue(false);
	}
}
