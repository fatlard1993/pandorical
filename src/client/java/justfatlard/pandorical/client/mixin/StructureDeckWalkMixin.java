package justfatlard.pandorical.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import justfatlard.pandorical.client.structure.StructureDecks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Legs walk at the pace of the deck, not of the sea. The client works out a walk animation from
 * how far an entity moved in the tick, so anyone stood on a sailing deck walked on the spot; the
 * deck's share of the move ({@link StructureDecks#deckStep}) is taken off first.
 */
@Mixin(LivingEntity.class)
public abstract class StructureDeckWalkMixin {

	@WrapOperation(method = "calculateEntityAnimation", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/util/Mth;length(DDD)D"))
	private double pandorical$walkOnDeck(double dx, double dy, double dz, Operation<Double> original,
										 @Local(argsOnly = true) boolean vertical) {
		Vec3 step = StructureDecks.deckStep((LivingEntity) (Object) this);
		if (step != null) {
			dx -= step.x;
			if (vertical) dy -= step.y;
			dz -= step.z;
		}
		return original.call(dx, dy, dz);
	}
}
