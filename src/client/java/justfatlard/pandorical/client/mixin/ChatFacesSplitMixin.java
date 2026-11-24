package justfatlard.pandorical.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import justfatlard.pandorical.client.chat.ChatFaces;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;

/**
 * Where chat splits a message into the lines it draws: one with a face wraps narrower, and its
 * first line is the one the face goes on. Only chat's own split counts, not anyone else's.
 */
@Mixin(ChatComponent.class)
public abstract class ChatFacesSplitMixin {

	@ModifyArg(method = "addMessageToDisplayQueue", index = 1, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/multiplayer/chat/GuiMessage;splitLines(Lnet/minecraft/client/gui/Font;I)Ljava/util/List;"))
	private int pandorical$narrower(int width, @Local(argsOnly = true) GuiMessage message) {
		return ChatFaces.wrapWidth(message, width);
	}

	@ModifyExpressionValue(method = "addMessageToDisplayQueue", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/multiplayer/chat/GuiMessage;splitLines(Lnet/minecraft/client/gui/Font;I)Ljava/util/List;"))
	private List<FormattedCharSequence> pandorical$first(List<FormattedCharSequence> lines, @Local(argsOnly = true) GuiMessage message) {
		ChatFaces.split(message, lines);
		return lines;
	}
}
