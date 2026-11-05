package justfatlard.pandorical.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import justfatlard.pandorical.shape.SupportMemo;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * CENTER and RIGID support compare a face with a fixed shape, so the answer is the face's alone:
 * remembered per face shape instead of worked out for each of a block's states. FULL already goes
 * through vanilla's own cache.
 */
@Mixin(targets = {"net.minecraft.world.level.block.SupportType$2", "net.minecraft.world.level.block.SupportType$3"})
public abstract class SupportTypeMemoMixin {

	@WrapOperation(method = "isSupporting", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/phys/shapes/Shapes;joinIsNotEmpty(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/BooleanOp;)Z"))
	private boolean pandorical$rememberedPerFace(VoxelShape face, VoxelShape support, BooleanOp op, Operation<Boolean> original) {
		if (op != BooleanOp.ONLY_SECOND) return original.call(face, support, op);
		return SupportMemo.remembered(face, support, () -> original.call(face, support, op));
	}
}
