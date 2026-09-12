package justfatlard.pandorical.client.mixin;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * {@code ItemStackTemplate.validate} swaps a stack failing {@code validateStrict} for
 * {@code EMPTY}, and on the client any count over the vanilla max fails: stack limits are raised
 * server-side only. An emptied bundle entry then throws in
 * {@code ItemStackTemplate.fromNonEmptyStack} when the pointer leaves the bundle, crashing the
 * render pass. Component validation returns before this call, so malformed stacks still fail.
 */
@Mixin(ItemStack.class)
public class StackValidationMixin {

	@Redirect(
		method = "validateStrict",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getMaxStackSize()I")
	)
	private static int pandorical$keepWhatTheServerSent(ItemStack stack) {
		return stack.getCount();
	}
}
