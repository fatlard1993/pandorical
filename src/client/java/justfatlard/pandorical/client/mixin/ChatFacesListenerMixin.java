package justfatlard.pandorical.client.mixin;

import com.mojang.authlib.GameProfile;
import justfatlard.pandorical.client.chat.ChatFaces;
import net.minecraft.client.multiplayer.chat.ChatListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Who sent the player message vanilla is about to add to chat. */
@Mixin(ChatListener.class)
public abstract class ChatFacesListenerMixin {

	@Inject(method = "showMessageToPlayer", at = @At("HEAD"))
	private void pandorical$showing(net.minecraft.network.chat.ChatType.Bound bound, net.minecraft.network.chat.PlayerChatMessage message,
			net.minecraft.network.chat.Component decorated, GameProfile sender, boolean onlyShowSecure, java.time.Instant received,
			CallbackInfoReturnable<Boolean> cir) {
		ChatFaces.showing(sender);
	}

	@Inject(method = "showMessageToPlayer", at = @At("RETURN"))
	private void pandorical$shown(CallbackInfoReturnable<Boolean> cir) {
		ChatFaces.showing(null);
	}
}
