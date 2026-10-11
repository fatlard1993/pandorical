package justfatlard.pandorical.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import justfatlard.pandorical.client.structure.StructureHolds;
import justfatlard.pandorical.structure.DryCells;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The client's half of a dry structure: nothing in its dry cells is in water, so a player walks
 * the hold of a ship at sea rather than swimming it. The server's half is {@code StructureDryMixin}.
 */
@Mixin(EntityFluidInteraction.class)
public abstract class StructureDryClientMixin {
	@WrapOperation(method = "update", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/BlockGetter;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"))
	private FluidState pandorical$dryInStructure(BlockGetter level, BlockPos pos, Operation<FluidState> original, Entity entity, boolean pushed) {
		FluidState fluid = original.call(level, pos);
		if (fluid.isEmpty() || !entity.level().isClientSide()) return fluid;
		Vec3 point = DryCells.pointFor(entity, pos);
		return StructureHolds.dryAt(point.x, point.y, point.z) ? Fluids.EMPTY.defaultFluidState() : fluid;
	}
}
