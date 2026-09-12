package justfatlard.pandorical.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.RecipeBookMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import justfatlard.pandorical.client.inventory.ClientInventoryButtons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/**
 * Clicks for the buttons {@link InventoryScreenMixin} draws. {@code InventoryScreen} inherits
 * {@code mouseClicked} from this class, so the hook lives here; HEAD runs before the recipe book,
 * which can consume the click. Extends the container screen only to reach {@code leftPos}.
 */
@Mixin(AbstractRecipeBookScreen.class)
public abstract class InventoryButtonClickMixin extends AbstractContainerScreen<RecipeBookMenu> {

	private InventoryButtonClickMixin() {
		super(null, null, null);
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void pandorical$clickInventoryButton(MouseButtonEvent click, boolean handled,
			CallbackInfoReturnable<Boolean> cir) {
		if (handled) return;
		if (!((Object) this instanceof InventoryScreen)) return;

		for (var button : ClientInventoryButtons.all()) {
			int bx = this.leftPos + button.screenX();
			int by = this.topPos + button.screenY();
			int size = button.size();
			if (click.x() < bx || click.x() >= bx + size) continue;
			if (click.y() < by || click.y() >= by + size) continue;

			ClientInventoryButtons.press(button);
			Minecraft.getInstance().getSoundManager().play(
				SimpleSoundInstance.forUI(
					SoundEvents.UI_BUTTON_CLICK, 1.0F));
			cir.setReturnValue(true);
			return;
		}
	}
}
