package justfatlard.pandorical.mixin;

import justfatlard.pandorical.form.Forms;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla passes a dialog's answer on without saying who sent it. Pandorical's own forms are taken
 * here, on the server thread and with the player, and everything else goes on as before.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class CustomClickActionMixin {
	@Inject(method = "handleCustomClickAction", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/server/MinecraftServer;handleCustomClickAction(Lnet/minecraft/resources/Identifier;Ljava/util/Optional;)V"),
		cancellable = true)
	private void pandorical$forms(ServerboundCustomClickActionPacket packet, CallbackInfo ci) {
		if ((Object) this instanceof ServerGamePacketListenerImpl game && Forms.handle(game.player, packet.id(), packet.payload())) {
			ci.cancel();
		}
	}
}
