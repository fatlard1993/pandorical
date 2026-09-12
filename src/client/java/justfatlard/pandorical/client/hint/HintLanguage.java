package justfatlard.pandorical.client.hint;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/** Wrapped around every language the game injects (LanguageHintMixin). See {@link InputHints}. */
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
