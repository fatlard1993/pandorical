package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.structure.StructureDecks;
import justfatlard.pandorical.protocol.UseStructureC2S;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Use, pressed at a block of a walkable structure - a door or a chest on a ship at sea - goes to
 * the server as a use of that block, which is drawn and not in the world for vanilla to find.
 *
 * <p>Left to vanilla when anything in the world is nearer, when the block is not one a mod answers,
 * and when the player sneaks with something in hand, which is how a player asks to use the item.
 */
@Mixin(Minecraft.class)
public abstract class StructureUseMixin {
	@Shadow private @Nullable LocalPlayer player;
	@Shadow private @Nullable HitResult hitResult;
	@Shadow private @Nullable MultiPlayerGameMode gameMode;
	@Shadow private int rightClickDelay;

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void pandorical$useOnStructure(CallbackInfo ci) {
		LocalPlayer player = this.player;
		if (player == null || this.gameMode == null || this.gameMode.isDestroying() || player.isHandsBusy()
				|| player.isSpectator()) return;
		if (player.isSecondaryUseActive()
				&& (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty())) return;
		if (!ClientPlayNetworking.canSend(UseStructureC2S.TYPE)) return;
		StructureDecks.Pick pick = StructureDecks.usePick(player, this.hitResult);
		if (pick == null) return;

		this.rightClickDelay = 4;
		ClientPlayNetworking.send(new UseStructureC2S(pick.structureId(), pick.x(), pick.y(), pick.z()));
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		ci.cancel();
	}
}
