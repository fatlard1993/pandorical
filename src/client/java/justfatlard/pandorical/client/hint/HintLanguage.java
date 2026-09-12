package justfatlard.pandorical.client.hint;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/**
 * The game's language, reading a key's {@code .controller} line instead while a pad is in use.
 * Wrapped around whatever language the game puts in place (see LanguageHintMixin), so a resource
 * reload or a change of language in the options keeps it.
 */
@Environment(EnvType.CLIENT)
public final class HintLanguage extends Language {
	private final Language base;

	public HintLanguage(Language base) {
		this.base = base;
	}

	public Language base() {
		return base;
	}

	@Override
	public String getOrDefault(String key, String fallback) {
		if (InputHints.controller()) {
			String variant = key + InputHints.SUFFIX;
			if (base.has(variant)) return InputHints.fill(base.getOrDefault(variant, fallback));
		}
		return base.getOrDefault(key, fallback);
	}

	@Override
	public boolean has(String key) {
		return base.has(key);
	}

	@Override
	public boolean isDefaultRightToLeft() {
		return base.isDefaultRightToLeft();
	}

	@Override
	public FormattedCharSequence getVisualOrder(FormattedText text) {
		return base.getVisualOrder(text);
	}
}
