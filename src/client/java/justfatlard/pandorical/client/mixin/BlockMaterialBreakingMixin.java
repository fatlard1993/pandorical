package justfatlard.pandorical.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import justfatlard.pandorical.client.renderer.ClientBlockMaterials;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A position the server gave a material is mined and broken as that material: the hit sound and
 * the cracks flying off the face while it is mined, and the crumbs as it goes. The block's own
 * state still says where it is solid, so the crumbs come off the shape it has there.
 */
@Mixin(ClientLevel.class)
public abstract class BlockMaterialBreakingMixin {
	/** Mining it, the hits and cracks: what the server's mining events and the first hit both play. */
	@WrapOperation(method = "addBreakingBlockEffects", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/multiplayer/ClientLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
	private BlockState pandorical$breakingMaterial(ClientLevel level, BlockPos pos, Operation<BlockState> original) {
		return ClientBlockMaterials.as(original.call(level, pos), pos);
	}

	/** Broken, the crumbs: given the state it was, with the position, by the break's level event. */
	@Inject(method = "addDestroyBlockEffect", at = @At("HEAD"), cancellable = true)
	private void pandorical$destroyMaterial(BlockPos pos, BlockState state, CallbackInfo ci) {
		BlockState material = ClientBlockMaterials.as(state, pos);
		if (material == state) return;
		ci.cancel();
		((ClientLevel) (Object) this).addDestroyBlockEffect(pos, material);
	}
}
