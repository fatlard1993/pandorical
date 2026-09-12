package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.hint.HintLanguage;
import net.minecraft.locale.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Every language the game puts in place goes in wrapped for controller hints. See InputHints.
 * At the one door rather than after each caller: loading, reloading and switching language all
 * come through here, and a caller added later does too.
 */
@Mixin(Language.class)
public abstract class LanguageHintMixin {
	@ModifyVariable(method = "inject", at = @At("HEAD"), argsOnly = true)
	private static Language pandorical$wrapForHints(Language language) {
		return language instanceof HintLanguage ? language : new HintLanguage(language);
	}
}
