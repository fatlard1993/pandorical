package justfatlard.pandorical.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import justfatlard.pandorical.client.renderer.ClientBlockMaterials;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A step on a position the server gave a material sounds like that material. Only the steps this
 * client plays itself, its own player's: the server's sounds for everyone else arrive as sounds.
 * The integrated server's entities walk through here too, and are left alone by the lookup.
 */
@Mixin(Entity.class)
public abstract class BlockMaterialStepMixin {
	@WrapOperation(method = "playStepSound", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
	private SoundType pandorical$stepMaterial(BlockState state, Operation<SoundType> original, @Local(argsOnly = true) BlockPos pos) {
		return original.call(ClientBlockMaterials.as(state, pos));
	}
}
