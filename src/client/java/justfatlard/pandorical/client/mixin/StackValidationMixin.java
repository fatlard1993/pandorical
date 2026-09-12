package justfatlard.pandorical.client.mixin;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Stops the client discarding a stack the server sent because it is too big to be one.
 *
 * <p>The third of these, and the deepest. {@code ContainerStackSizeMixin} and
 * {@code MerchantStackSizeMixin} stop a screen trimming an oversized stack down; this one is
 * about the stacks that never survive to be trimmed. {@code ItemStackTemplate.validate} asks
 * {@code ItemStack.validateStrict} whether a materialised stack is legal, and throws the stack
 * away for {@code ItemStack.EMPTY} when it is not - which on this side means any count above
 * sixty-four, because the mod that lifts stack limits runs server-side only.
 *
 * <p>An empty stack in a bundle is not a cosmetic fault the way a trimmed count is. Bundle
 * contents are rebuilt from templates every time the pointer leaves the bundle, and
 * {@code BundleContents$Mutable.toImmutable} hands each one to
 * {@code ItemStackTemplate.fromNonEmptyStack}, whose first act is to throw on an empty stack.
 * Ninety-three string in a bundle therefore crashed the client on mouse-out, in the render
 * pass, with no mod anywhere in the trace.
 *
 * <p>Only the size complaint goes. Component validation runs first inside
 * {@code validateStrict} and returns before this call is reached, so a genuinely malformed
 * stack is still refused - it is only the count this side stops second-guessing, on the same
 * grounds as its two siblings: the server is the authority on what it put in a slot.
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
