package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.renderer.PositionalTintStore;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** While a painted block animates, the particles it throws are painted too. */
@Mixin(ClientLevel.class)
public abstract class BlockAnimateTintMixin {
	@Redirect(method = "doAnimateTick", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/block/Block;animateTick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
	private void pandorical$animateTinted(Block block, BlockState state, Level level, BlockPos pos, RandomSource random) {
		PositionalTintStore.emitFrom(state, pos);
		try {
			block.animateTick(state, level, pos, random);
		} finally {
			PositionalTintStore.doneEmitting();
		}
	}
}
