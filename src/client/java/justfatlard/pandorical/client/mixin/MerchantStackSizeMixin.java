package justfatlard.pandorical.client.mixin;

import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** {@link ContainerStackSizeMixin} for the trade slots; the payment bookkeeping still runs. */
@Mixin(MerchantContainer.class)
public class MerchantStackSizeMixin {

	@Redirect(
		method = "setItem",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;limitSize(I)V")
	)
	private void pandorical$keepWhatTheServerSent(ItemStack stack, int limit) {
		// Deliberately nothing.
	}
}
