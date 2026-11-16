package justfatlard.pandorical.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import justfatlard.pandorical.client.renderer.ClientBlockMaterials;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Landing hard on a position the server gave a material sounds like that material, for the
 * landing this client plays itself. The position is the one vanilla reads the block from.
 */
@Mixin(LivingEntity.class)
public abstract class BlockMaterialFallMixin {
	@WrapOperation(method = "playBlockFallSound", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
	private SoundType pandorical$fallMaterial(BlockState state, Operation<SoundType> original) {
		if (!ClientBlockMaterials.any) return original.call(state);
		LivingEntity self = (LivingEntity) (Object) this;
		BlockPos pos = new BlockPos(Mth.floor(self.getX()), Mth.floor(self.getY() - 0.2F), Mth.floor(self.getZ()));
		return original.call(ClientBlockMaterials.as(state, pos));
	}
}
