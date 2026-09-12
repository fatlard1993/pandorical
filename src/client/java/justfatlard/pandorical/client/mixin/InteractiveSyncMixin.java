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
 * The client copy of a server-only block has none of its behaviour, so vanilla predicts an
 * unhandled use and places the held item, which the server then undoes. Hooked here rather than
 * on a block class because a synced block without properties is a plain {@code Block}. The use
 * packet is still sent after this returns.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class InteractiveSyncMixin {

	@Inject(method = "performUseItemOn", at = @At("HEAD"), cancellable = true)
	private void pandorical$leaveItToTheServer(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
			CallbackInfoReturnable<InteractionResult> callback) {
		if (player.level() == null) return;

		if (ContentManager.isInteractive(player.level().getBlockState(hit.getBlockPos()))) {
			callback.setReturnValue(InteractionResult.SUCCESS);
		}
	}
}
