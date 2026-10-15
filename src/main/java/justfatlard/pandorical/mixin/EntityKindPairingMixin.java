package justfatlard.pandorical.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import justfatlard.pandorical.entitykind.EntityKinds;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The one packet that names an entity's type, rewritten per player: a vanilla client shown a
 * registered entity with a fallback is told it is the base. Everything after it - data,
 * attributes, equipment - reads the same either way, which is the contract the base exists for.
 */
@Mixin(ServerEntity.class)
public abstract class EntityKindPairingMixin {

	@WrapOperation(method = "sendPairingData", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/Entity;getAddEntityPacket(Lnet/minecraft/server/level/ServerEntity;)Lnet/minecraft/network/protocol/Packet;"))
	private Packet<?> pandorical$readableType(Entity entity, ServerEntity serverEntity, Operation<Packet<?>> original,
			@Local(argsOnly = true) ServerPlayer player) {
		Packet<?> packet = original.call(entity, serverEntity);
		return packet instanceof ClientboundAddEntityPacket add ? EntityKinds.forPlayer(add, player) : packet;
	}
}
