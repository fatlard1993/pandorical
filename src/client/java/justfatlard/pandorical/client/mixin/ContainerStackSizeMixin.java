package justfatlard.pandorical.client.mixin;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Stops every container screen trimming the stacks the server sent it.
 *
 * <p>{@code SimpleContainer.setItem} finishes with {@code stack.limitSize(getMaxStackSize(stack))},
 * and on a client that answer is sixty-four: the mod that lifts stack limits runs server-side
 * only, so this side still has vanilla's numbers and quietly cuts anything larger down as it
 * arrives.
 *
 * <p>This is not one screen's problem. {@code ChestMenu}, {@code HopperMenu},
 * {@code DispenserMenu}, {@code ShulkerBoxMenu} and {@code BrewingStandMenu} each build a
 * SimpleContainer on the client to receive the server's contents, so a chest holding two hundred
 * of something drew as sixty-four in all of them - the same fault found in the builder's table
 * and again in the trade screen, one layer further down than either.
 *
 * <p>Only the trim goes. The stack is still stored by the line above, and nothing here changes
 * what the client believes about stacking when the player moves things: that still runs through
 * the slot's own limit, so a client attached to a server without oversized stacks behaves
 * exactly as before, having never been sent one.
 */
@Mixin(SimpleContainer.class)
public class ContainerStackSizeMixin {

	@Redirect(
		method = "setItem",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;limitSize(I)V"),
		require = 1
	)
	private void pandorical$keepWhatTheServerSent(ItemStack stack, int limit) {
		// Deliberately nothing.
	}
}
