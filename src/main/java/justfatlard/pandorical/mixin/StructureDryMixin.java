package justfatlard.pandorical.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import justfatlard.pandorical.structure.DryCells;
import justfatlard.pandorical.structure.StructureRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The server's half of a dry structure: nothing in its dry cells is in water. Every way an entity
 * is wet - swimming, the air it has left, the current pushing it - is read from this one pass, so
 * nobody drowns in the hold of a ship at sea.
 */
@Mixin(EntityFluidInteraction.class)
public abstract class StructureDryMixin {
	@WrapOperation(method = "update", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/BlockGetter;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"))
	private FluidState pandorical$dryInStructure(BlockGetter level, BlockPos pos, Operation<FluidState> original, Entity entity, boolean pushed) {
		FluidState fluid = original.call(level, pos);
		if (fluid.isEmpty() || !(entity.level() instanceof ServerLevel)) return fluid;
		Vec3 point = DryCells.pointFor(entity, pos);
		return StructureRegistry.INSTANCE.dryAt(entity, point.x, point.y, point.z) ? Fluids.EMPTY.defaultFluidState() : fluid;
	}
}
