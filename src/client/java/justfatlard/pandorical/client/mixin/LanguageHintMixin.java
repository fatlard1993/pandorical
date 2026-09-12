package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.hint.HintLanguage;
import net.minecraft.locale.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Loading, reloading and switching language all pass through {@code Language.inject}. */
@Mixin(Language.class)
public abstract class LanguageHintMixin {
	@ModifyVariable(method = "inject", at = @At("HEAD"), argsOnly = true)
	private static Language pandorical$wrapForHints(Language language) {
		return language instanceof HintLanguage ? language : new HintLanguage(language);
	}
}
