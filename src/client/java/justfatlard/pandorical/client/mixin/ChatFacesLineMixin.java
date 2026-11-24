package justfatlard.pandorical.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import justfatlard.pandorical.client.chat.ChatFaces;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Where chat draws, or only measures for clicks, each line: indented for a face, so a click lands
 * on the same text the eye sees.
 */
@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$1")
public abstract class ChatFacesLineMixin {

	@WrapOperation(method = "accept", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;handleMessage(IFLnet/minecraft/util/FormattedCharSequence;)Z"))
	private boolean pandorical$face(@org.spongepowered.asm.mixin.injection.Coerce Object access, int y, float opacity, FormattedCharSequence text,
			Operation<Boolean> original, @Local(argsOnly = true) GuiMessage.Line line) {
		return original.call(access, y, opacity, ChatFaces.line(line, y, opacity, justfatlard.pandorical.client.chat.ChatGraphics.of(access)));
	}
}
