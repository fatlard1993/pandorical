package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.structure.StructureHolds;
import net.minecraft.client.Camera;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A camera in the dry hold of a ship at sea sees through air, not water. */
@Mixin(Camera.class)
public abstract class StructureDryCameraMixin {
	@Shadow
	public abstract Vec3 position();

	@Inject(method = "getFluidInCamera", at = @At("HEAD"), cancellable = true)
	private void pandorical$dryHold(CallbackInfoReturnable<FogType> cir) {
		Vec3 at = position();
		if (StructureHolds.dryAt(at.x, at.y, at.z)) cir.setReturnValue(FogType.NONE);
	}
}
