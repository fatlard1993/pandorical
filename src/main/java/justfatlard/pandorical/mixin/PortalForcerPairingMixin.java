package justfatlard.pandorical.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import justfatlard.pandorical.portal.PortalPairing;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.portal.PortalForcer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Passes over portals already paired with another while an unpaired portal looks for its exit;
 * see {@link PortalPairing#searchingFor}. Around the {@code min} that picks, as Player Portals'
 * own filter is, so both apply to the same search.
 */
@Mixin(PortalForcer.class)
public abstract class PortalForcerPairingMixin {
	@Shadow
	@Final
	private ServerLevel level;

	@WrapOperation(method = "findClosestPortalPosition", at = @At(value = "INVOKE",
		target = "Ljava/util/stream/Stream;min(Ljava/util/Comparator;)Ljava/util/Optional;"))
	private Optional<BlockPos> pandorical$notIntoAnotherPair(Stream<BlockPos> candidates,
			Comparator<? super BlockPos> nearest, Operation<Optional<BlockPos>> original) {
		Map<BlockPos, Boolean> judged = new HashMap<>();
		return original.call(candidates.filter(pos -> !PortalPairing.isTaken(level, pos, judged)), nearest);
	}
}
