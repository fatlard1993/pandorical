package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.actions.ActionMenus;
import justfatlard.pandorical.client.keybind.KeybindManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.dialog.DialogConnectionAccess;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.dialog.Dialog;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The server ships a quick-actions dialog for players without Pandorical. A Pandorical client has
 * the menus it stands in for, so choosing it opens those instead.
 *
 * <p>Unless a mod's key shares the quick-actions key (both G by default: poopsmith's poop). The
 * game handles quick actions first, and opening any screen releases every key, so the mod's press
 * would be lost before it was read. Then nothing opens and the press is the mod's; the menus are
 * still on their own key.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class QuickActionsDialogMixin {
	@Unique
	private static final ResourceKey<Dialog> PANDORICAL$QUICK_ACTIONS =
		ResourceKey.create(Registries.DIALOG, Identifier.fromNamespaceAndPath("pandorical", "quick_actions"));

	@Inject(method = "showDialog(Lnet/minecraft/core/Holder;Lnet/minecraft/client/gui/screens/dialog/DialogConnectionAccess;Lnet/minecraft/client/gui/screens/Screen;)V",
		at = @At("HEAD"), cancellable = true)
	private void pandorical$openMenus(Holder<Dialog> dialog, DialogConnectionAccess access, @Nullable Screen activeScreen, CallbackInfo ci) {
		if (!dialog.is(PANDORICAL$QUICK_ACTIONS)) return;
		if (KeybindManager.claimsSameKey(Minecraft.getInstance().options.keyQuickActions)) {
			ci.cancel();
			return;
		}
		if (ActionMenus.openHub()) ci.cancel();
	}
}
