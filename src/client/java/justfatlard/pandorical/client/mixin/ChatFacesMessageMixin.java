package justfatlard.pandorical.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import justfatlard.pandorical.client.chat.ChatFaces;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Each message chat makes keeps whose it is. */
@Mixin(ChatComponent.class)
public abstract class ChatFacesMessageMixin {

	@ModifyExpressionValue(method = "addMessage", at = @At(value = "NEW", target = "net/minecraft/client/multiplayer/chat/GuiMessage"))
	private GuiMessage pandorical$made(GuiMessage message) {
		return ChatFaces.made(message);
	}
}
