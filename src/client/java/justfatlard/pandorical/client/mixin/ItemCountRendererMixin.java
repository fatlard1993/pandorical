package justfatlard.pandorical.client.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the whole decoration pass for oversized stacks, so they draw no durability bar or
 * cooldown overlay. The label is right-anchored so it never spills into the next slot.
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class ItemCountRendererMixin {
	@Unique
	private static final int OVERSIZE_THRESHOLD = 100;

	@Inject(
		method = "itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void pandorical$compactOversizedCount(Font font, ItemStack stack, int x, int y, CallbackInfo ci) {
		pandorical$drawCompactLabel(font, stack, x, y, ci);
	}

	@Inject(
		method = "itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void pandorical$compactOversizedCountLabeled(Font font, ItemStack stack, int x, int y,
			String customLabel, CallbackInfo ci) {
		if (customLabel != null) return;
		pandorical$drawCompactLabel(font, stack, x, y, ci);
	}

	@Unique
	private void pandorical$drawCompactLabel(Font font, ItemStack stack, int x, int y, CallbackInfo ci) {
		if (stack.isEmpty() || stack.getCount() < OVERSIZE_THRESHOLD) return;

		String label = pandorical$abbreviate(stack.getCount());
		GuiGraphicsExtractor self = (GuiGraphicsExtractor) (Object) this;
		int textWidth = font.width(label);

		final float scale = 0.75f;
		Matrix3x2fStack pose = self.pose();
		pose.pushMatrix();
		pose.translate(x + 17f, y + 11f);
		pose.scale(scale, scale);
		self.text(font, label, -textWidth, 0, -1, true);
		pose.popMatrix();

		ci.cancel();
	}

	@Unique
	private static String pandorical$abbreviate(int count) {
		if (count >= 1_000_000_000) {
			return (count / 1_000_000_000) + "b";
		} else if (count >= 10_000_000) {
			return (count / 1_000_000) + "m";
		} else if (count >= 1_000_000) {
			int major = count / 1_000_000;
			int minor = (count % 1_000_000) / 100_000;
			return major + "." + minor + "m";
		} else if (count >= 1_000) {
			return (count / 1_000) + "k";
		} else {
			return String.valueOf(count);
		}
	}
}
