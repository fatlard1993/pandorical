package justfatlard.pandorical.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Abbreviates large stack counts in inventory slots.
 *
 * Vanilla calls String.valueOf(count) in the private itemCount method.
 * We wrap that call to produce abbreviated text for large numbers:
 *   1-999:           "1", "64", "999" (vanilla behavior)
 *   1,000-9,999:     "1.2K"
 *   10,000-999,999:  "15K", "999K"
 *   1,000,000+:      "1.2M", "15M"
 *   1,000,000,000+:  "1.2B"
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class ItemCountRendererMixin {
	@WrapOperation(
		method = "itemCount",
		at = @At(
			value = "INVOKE",
			target = "Ljava/lang/String;valueOf(I)Ljava/lang/String;"
		)
	)
	private String pandorical$abbreviateStackCount(int count, Operation<String> original) {
		if (count < 1_000) return original.call(count);
		if (count < 10_000) return pandorical$formatDecimal(count, 1_000, "K");
		if (count < 1_000_000) return (count / 1_000) + "K";
		if (count < 10_000_000) return pandorical$formatDecimal(count, 1_000_000, "M");
		if (count < 1_000_000_000) return (count / 1_000_000) + "M";
		return pandorical$formatDecimal(count, 1_000_000_000, "B");
	}

	@Unique
	private static String pandorical$formatDecimal(int count, int divisor, String suffix) {
		int whole = count / divisor;
		int frac = (count % divisor) * 10 / divisor;
		return frac > 0 ? whole + "." + frac + suffix : whole + suffix;
	}
}
