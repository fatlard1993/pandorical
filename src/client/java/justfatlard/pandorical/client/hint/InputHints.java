package justfatlard.pandorical.client.hint;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.locale.Language;

/**
 * While a pad is in use, a translation {@code key} with a {@code key.controller} line reads that
 * line, its bracketed controls such as {@code [use]} named by the pad driver. A word the driver
 * does not name is left as written. The driver calls {@link #controller} and {@link #keyboard}.
 */
@Environment(EnvType.CLIENT)
public final class InputHints {
	private InputHints() {}

	public static final String SUFFIX = ".controller";

	private static final Pattern CONTROL = Pattern.compile("\\[([a-z_]+)]");

	/** Null on the keyboard. */
	private static volatile Function<String, String> controls;

	public static boolean controller() {
		return controls != null;
	}

	/** {@code names} maps a control word to the pad's label, or null; call again on a new pad. */
	public static void controller(Function<String, String> names) {
		if (names == controls) return;
		controls = names;
		refresh();
	}

	public static void keyboard() {
		if (controls == null) return;
		controls = null;
		refresh();
	}

	public static String fill(String line) {
		Function<String, String> names = controls;
		if (names == null || line.indexOf('[') < 0) return line;
		Matcher matcher = CONTROL.matcher(line);
		StringBuilder out = new StringBuilder();
		while (matcher.find()) {
			String name = names.apply(matcher.group(1));
			matcher.appendReplacement(out, Matcher.quoteReplacement(name != null ? name : matcher.group()));
		}
		matcher.appendTail(out);
		return out.toString();
	}

	/** Translated components cache their text until a new language is injected. */
	private static void refresh() {
		Language current = Language.getInstance();
		if (current instanceof HintLanguage hints) Language.inject(new HintLanguage(hints.base()));
	}
}
