package justfatlard.pandorical.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import justfatlard.pandorical.client.renderer.ClientBlockMaterials;
import net.minecraft.client.renderer.LevelEventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A position the server gave a material breaks with that material's sound. The breaker's client
 * plays its own break before the server clears the material, so the material is still set when
 * this runs, and everyone else's client hears it from the server's event at the same position.
 * Only the break event asks a block for its sound here.
 */
@Mixin(LevelEventHandler.class)
public abstract class BlockMaterialDestroyMixin {
	@WrapOperation(method = "levelEvent", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
	private SoundType pandorical$breakMaterial(BlockState state, Operation<SoundType> original, @Local(argsOnly = true) BlockPos pos) {
		return original.call(ClientBlockMaterials.as(state, pos));
	}
}
