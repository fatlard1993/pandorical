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
 * Compact, scaled count labels for OVERSIZED stacks (100+), absorbed from
 * stackz's client so stack-size mods need no client jar at all. Vanilla-range
 * stacks keep the untouched vanilla decoration pass (count text, durability
 * bar, cooldown overlay).
 *
 * <p>For 100+ stacks the whole decoration pass is replaced with a 0.75-scale
 * label right-anchored at (x+17, y+11): the text's right edge stays fixed so
 * abbreviations never overflow into the next slot. The durability bar and
 * cooldown overlay are suppressed for those stacks only, stackz's original
 * trade-off scoped narrowly: stacks above vanilla sizes come from stack-size
 * mods, which unlock non-durability items.
 *
 * <p>Abbreviation matches stackz exactly: 1k, 15k, 1.2m, 15m, 1b.
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class ItemCountRendererMixin {
	@Unique
	private static final int OVERSIZE_THRESHOLD = 100;

	@Inject(
		method = "itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V",
		at = @At("HEAD"),
		cancellable = true,
		require = 1
	)
	private void pandorical$compactOversizedCount(Font font, ItemStack stack, int x, int y, CallbackInfo ci) {
		pandorical$drawCompactLabel(font, stack, x, y, ci);
	}

	@Inject(
		method = "itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
		at = @At("HEAD"),
		cancellable = true,
		require = 1
	)
	private void pandorical$compactOversizedCountLabeled(Font font, ItemStack stack, int x, int y,
			String customLabel, CallbackInfo ci) {
		// A caller-supplied label overrides the count; leave that to vanilla
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
