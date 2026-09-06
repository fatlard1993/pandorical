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
 * Stop drawing the leaves nobody can see.
 *
 * <p>Vanilla hides leaf-against-leaf faces only on Fast graphics - that is exactly what the
 * {@code cutoutLeaves} flag in {@code LeavesBlock.skipRendering} is. On Fancy it hides none of
 * them, so a solid canopy draws every one of its interior faces. Measured against this suite's own
 * ancient trees, one big crown is about 1,235 leaf blocks drawing roughly 7,400 faces on Fancy
 * against 1,100 on Fast: a six-fold difference, for blocks that are buried inside a ball of leaves.
 *
 * <p>Taken here rather than at {@code skipRendering} because that hook is handed two block states
 * and a direction and no position, so it cannot tell a leaf on the surface from one in the middle -
 * the only rule available there is "cull every leaf face", which is Fast wearing Fancy's texture
 * and lets daylight through the gaps in a canopy. This one has the level and the position, so it
 * can keep the shell.
 */
@Mixin(ModelBlockRenderer.class)
public abstract class LeafCullingMixin {

	/**
	 * At RETURN and only when the answer was yes, so the work is skipped entirely for a face vanilla
	 * has already decided against - which on Fast is every face this would have looked at.
	 */
	@Inject(method = "shouldRenderFace", at = @At("RETURN"), cancellable = true)
	private void pandorical$cullBuriedLeaves(BlockAndTintGetter level, BlockState state,
			Direction direction, BlockPos neighbourPos, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ()) return;
		if (LeafCulling.isBuriedLeafFace(level, state, neighbourPos)) cir.setReturnValue(false);
	}
}
