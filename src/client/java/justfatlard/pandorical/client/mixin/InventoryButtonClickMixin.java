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

/**
 * Answers a press on one of the buttons drawn on the player's own inventory panel.
 *
 * <p><b>Not on {@code InventoryScreen}, where the drawing lives.</b> That class does not declare
 * {@code mouseClicked} - it inherits one - so an injection aimed there finds no method.
 *
 * <p>{@code AbstractRecipeBookScreen} is the class that actually declares it, and HEAD is before
 * the recipe book gets its own look at the click - which matters, because the book can answer and
 * return without ever reaching the container screen below it.
 *
 * <p>Extends the container screen for the same reason its neighbour does: purely to reach the
 * protected {@code leftPos}/{@code topPos} the buttons are anchored to.
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
		// Only the player's own inventory draws these, so only it should answer for them.
		if (!((Object) this instanceof InventoryScreen)) return;

		for (var button : justfatlard.pandorical.client.inventory.ClientInventoryButtons.all()) {
			int bx = this.leftPos + button.screenX();
			int by = this.topPos + button.screenY();
			int size = button.size();
			if (click.x() < bx || click.x() >= bx + size) continue;
			if (click.y() < by || click.y() >= by + size) continue;

			justfatlard.pandorical.client.inventory.ClientInventoryButtons.press(button);
			net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
				net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
					net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
			cir.setReturnValue(true);
			return;
		}
	}
}
