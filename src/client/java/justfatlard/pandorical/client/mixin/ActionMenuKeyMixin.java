package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.actions.ActionMenus;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * An action menu opens on its key's press, once the game has done with the press: opened any
 * earlier, the game would hand the same press to the menu, which closes on its own key. And if
 * the key opened something of the game's own, the menu stays shut rather than covering it.
 */
@Mixin(KeyboardHandler.class)
public abstract class ActionMenuKeyMixin {

	@Inject(method = "keyPress", at = @At("TAIL"))
	private void pandorical$openActionMenu(long window, int action, KeyEvent event, CallbackInfo ci) {
		ActionMenus.keyPressed(action, event);
	}
}
