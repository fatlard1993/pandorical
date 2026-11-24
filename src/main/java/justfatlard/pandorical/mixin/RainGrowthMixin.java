package justfatlard.pandorical.mixin;

import justfatlard.pandorical.rules.RainGrowth;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every random tick in the game comes through here, so rain reaches any crop rather than the ones that
 * extend a vanilla class.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class RainGrowthMixin {

	@Inject(method = "randomTick", at = @At("TAIL"))
	private void pandorical$rainGrowth(ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		RainGrowth.afterRandomTick((BlockState) (Object) this, level, pos, random);
	}
}
