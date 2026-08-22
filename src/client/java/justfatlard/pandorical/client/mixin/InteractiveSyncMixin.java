package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.content.ContentManager;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops the client guessing at a right-click the server is going to answer.
 *
 * <p>The client's copy of a server-only block carries none of the real block's behaviour, so
 * vanilla's prediction sees an unhandled click and does what unhandled means: places whatever is
 * in hand. The server opens a screen and places nothing. The player watches a block appear and
 * vanish, and their stack count stays one short until some later packet happens to correct it.
 *
 * <p>Here rather than on the block class, because there is no one block class to put it on. What
 * the client builds for a synced block depends on what the block declared: with state properties
 * it gets a DynamicBlock, as a slab a SlabBlock, and with neither - which is the builder's table,
 * a mailbox, a bat box - a plain {@code new Block(props)} with nothing of ours in it at all. An
 * override on DynamicBlock therefore missed exactly the blocks that needed it most.
 *
 * <p>The packet is built after this returns and sent either way, so cancelling the prediction
 * costs nothing but the guess. Only ever fires for a block the server declared interactive: a
 * synced block that really is inert, like a cloud, has to keep letting people build against it.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class InteractiveSyncMixin {

	@Inject(method = "performUseItemOn", at = @At("HEAD"), cancellable = true, require = 1)
	private void pandorical$leaveItToTheServer(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
			CallbackInfoReturnable<InteractionResult> callback) {
		if (player.level() == null) return;

		if (ContentManager.isInteractive(player.level().getBlockState(hit.getBlockPos()))) {
			callback.setReturnValue(InteractionResult.SUCCESS);
		}
	}
}
