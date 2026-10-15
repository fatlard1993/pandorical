package justfatlard.pandorical.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import justfatlard.pandorical.entitykind.EntityKinds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A registered entity is never paired with a client that cannot read it. Here rather than on
 * {@code Entity.broadcastToPlayer}, which a subclass may override without calling up.
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class EntityKindTrackingMixin {

	@WrapOperation(method = "updatePlayer", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/Entity;broadcastToPlayer(Lnet/minecraft/server/level/ServerPlayer;)Z"))
	private boolean pandorical$onlyIfReadable(Entity entity, ServerPlayer player, Operation<Boolean> original) {
		return original.call(entity, player) && EntityKinds.mayShow(entity, player);
	}
}
