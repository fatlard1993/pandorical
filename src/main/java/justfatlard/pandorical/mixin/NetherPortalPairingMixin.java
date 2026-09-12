package justfatlard.pandorical.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import justfatlard.pandorical.portal.PortalPairing;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.portal.PortalForcer;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/** Replaces only vanilla's exit search; the rest of the trip runs vanilla's code on the partner. */
@Mixin(NetherPortalBlock.class)
public abstract class NetherPortalPairingMixin {

	@WrapOperation(method = "getExitPortal", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/portal/PortalForcer;findClosestPortalPosition(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/level/border/WorldBorder;)Ljava/util/Optional;"))
	private Optional<BlockPos> pandorical$pairedExit(PortalForcer forcer, BlockPos approximateExit, boolean toNether,
			WorldBorder border, Operation<Optional<BlockPos>> original, ServerLevel newLevel, Entity entity,
			BlockPos entry, BlockPos approximateExitArg, boolean toNetherArg, WorldBorder borderArg) {
		if (entity.level() instanceof ServerLevel from) {
			BlockPos partner = PortalPairing.partnerFor(from, entry, newLevel);
			if (partner != null) return Optional.of(partner);
		}
		return original.call(forcer, approximateExit, toNether, border);
	}

	@Inject(method = "getPortalDestination", at = @At("RETURN"))
	private void pandorical$rememberTrip(ServerLevel level, Entity entity, BlockPos pos,
			CallbackInfoReturnable<TeleportTransition> cir) {
		PortalPairing.remember(level, pos, cir.getReturnValue());
	}
}
