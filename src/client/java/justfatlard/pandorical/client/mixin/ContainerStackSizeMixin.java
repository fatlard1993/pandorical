package justfatlard.pandorical.client.mixin;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * {@code SimpleContainer.setItem} trims each stack to the client's vanilla max, but stack limits
 * are raised server-side only, so chest, hopper, dispenser, shulker and brewing menus drew
 * oversized stacks short. Player moves still stack by the slot's own limit.
 */
@Mixin(SimpleContainer.class)
public class ContainerStackSizeMixin {

	@Redirect(
		method = "setItem",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;limitSize(I)V")
	)
	private void pandorical$keepWhatTheServerSent(ItemStack stack, int limit) {
		// Deliberately nothing.
	}
}
