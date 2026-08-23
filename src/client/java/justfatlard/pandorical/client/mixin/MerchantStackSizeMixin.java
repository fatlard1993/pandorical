package justfatlard.pandorical.client.mixin;

import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Stops the trade screen trimming a stack the server already accepted.
 *
 * <p>{@code MerchantContainer.setItem} finishes with
 * {@code stack.limitSize(getMaxStackSize(stack))}, and on a client that answer is sixty-four:
 * the mod that lifts stack limits runs server-side only, so this side still has vanilla's
 * numbers. A trade paid with two hundred emeralds therefore drew as sixty-four, and the slot
 * disagreed with the server about what was in it.
 *
 * <p>The same shape of fault as the one in Pandorical's own menu container, and the same
 * answer: this side is not the authority on what the server put in a slot, so it carries the
 * number across rather than second-guessing it.
 *
 * <p>Only the trim is removed. The stack is still stored by the line above, and the payment
 * bookkeeping below it still runs.
 */
@Mixin(MerchantContainer.class)
public class MerchantStackSizeMixin {

	@Redirect(
		method = "setItem",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;limitSize(I)V"),
		require = 1
	)
	private void pandorical$keepWhatTheServerSent(ItemStack stack, int limit) {
		// Deliberately nothing.
	}
}
