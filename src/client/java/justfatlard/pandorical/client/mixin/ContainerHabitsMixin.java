package justfatlard.pandorical.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import justfatlard.pandorical.client.settings.ContainerHabits;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import justfatlard.pandorical.client.screen.NavigationScroll;

/**
 * The wheel moves one item across; an empty-hand drag moves every stack it crosses. Both are
 * sent as the ordinary clicks a player could make, so the server needs no support for them.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerHabitsMixin {

	@Shadow protected Slot hoveredSlot;

	@Shadow protected abstract void slotClicked(Slot slot, int slotId, int button, ContainerInput input);

	private AbstractContainerMenu pandorical$menu() {
		return ((AbstractContainerScreen<?>) (Object) this).getMenu();
	}

	/** mouseDragged fires every frame, so each slot is moved at most once per drag. */
	private final Set<Integer> pandorical$swept = new HashSet<>();

	@Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
	private void pandorical$wheelMoves(double mouseX, double mouseY, double scrollX, double scrollY,
			CallbackInfoReturnable<Boolean> cir) {
		if (!ContainerHabits.enabled()) return;
		if (NavigationScroll.isActive()) return;
		if (scrollY == 0 || this.hoveredSlot == null || !this.hoveredSlot.hasItem()) return;
		if (!pandorical$menu().getCarried().isEmpty()) return;

		if ((scrollY > 0) != pandorical$isPlayerSide(this.hoveredSlot)) return;

		pandorical$moveOne(this.hoveredSlot);
		cir.setReturnValue(true);
	}

	/**
	 * Left quick-moves each slot crossed, right moves one item from each. Vanilla's drag only acts
	 * with a stack on the cursor, so an empty hand does not collide with it.
	 */
	@Inject(method = "mouseDragged", at = @At("HEAD"))
	private void pandorical$dragMoves(MouseButtonEvent event, double dragX, double dragY,
			CallbackInfoReturnable<Boolean> cir) {
		if (!ContainerHabits.enabled()) return;
		if (!pandorical$menu().getCarried().isEmpty()) return;
		if (this.hoveredSlot == null || !this.hoveredSlot.hasItem()) return;

		boolean rightButton = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
		if (!rightButton && event.button() != InputConstants.MOUSE_BUTTON_LEFT) return;
		if (!pandorical$swept.add(this.hoveredSlot.index)) return;

		if (rightButton) {
			pandorical$moveOne(this.hoveredSlot);
		} else {
			slotClicked(this.hoveredSlot, this.hoveredSlot.index, 0, ContainerInput.QUICK_MOVE);
		}
	}

	/**
	 * Pick the stack up, right-click one onto the target, put the rest back. With a stack of one
	 * the third click is an empty hand on an empty slot, a no-op.
	 */
	private void pandorical$moveOne(Slot from) {
		Slot target = pandorical$landingSlot(from);
		if (target == null) return;

		slotClicked(from, from.index, 0, ContainerInput.PICKUP);
		slotClicked(target, target.index, 1, ContainerInput.PICKUP);
		slotClicked(from, from.index, 0, ContainerInput.PICKUP);
	}

	private Slot pandorical$landingSlot(Slot from) {
		boolean fromPlayer = pandorical$isPlayerSide(from);
		ItemStack moving = from.getItem();
		Slot empty = null;

		for (Slot candidate : pandorical$menu().slots) {
			if (pandorical$isPlayerSide(candidate) == fromPlayer) continue;
			if (!candidate.mayPlace(moving)) continue;

			ItemStack held = candidate.getItem();
			if (held.isEmpty()) {
				if (empty == null) empty = candidate;
				continue;
			}
			if (ItemStack.isSameItemSameComponents(held, moving)
					&& held.getCount() < candidate.getMaxStackSize(held)) {
				return candidate;
			}
		}
		return empty;
	}

	private static boolean pandorical$isPlayerSide(Slot slot) {
		return slot.container instanceof Inventory;
	}

	@Inject(method = "mouseReleased", at = @At("HEAD"))
	private void pandorical$endSweep(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
		pandorical$swept.clear();
	}
}
