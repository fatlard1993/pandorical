package justfatlard.pandorical.client.hint;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.locale.Language;

/**
 * Which hands the player is using, so the game's hints can name the controls in them.
 *
 * <p>A hint that says "Hold Space" is no use to somebody on the couch with a pad. While a
 * controller is what the player last touched, any translation {@code key} that also has a
 * {@code key.controller} line reads that line instead, with its bracketed controls - {@code
 * [confirm]}, {@code [use]} - named the way the pad in their hands prints them. A key with no
 * such line reads as it always did, and so does everything on a keyboard, and everything on a
 * client without this mod, which never asks for the variant at all. The lang files carry the
 * wording; nothing about a hint has to be decided in code.
 *
 * <p>Whoever drives the pad says so: couch-controls calls {@link #controller} when the pad is
 * used and {@link #keyboard} when the keys or the mouse are. Nothing here knows about pads.
 *
 * <p>The controls a {@code .controller} line may name are whatever the driver answers for; the
 * suite's own is couch-controls, which answers {@code confirm}, {@code back}, {@code jump},
 * {@code sneak}, {@code drop}, {@code inventory}, {@code use}, {@code attack}, {@code move},
 * {@code look}, {@code pointer}, {@code navigate}, {@code scroll_up}, {@code scroll_down},
 * {@code hotbar_prev}, {@code hotbar_next}, {@code sprint}, {@code swap_hands}, {@code pause} and
 * {@code player_list}. A bracketed word it does not answer for is left as written.
 */
@Environment(EnvType.CLIENT)
public final class InputHints {
	private InputHints() {}

	public static final String SUFFIX = ".controller";

	private static final Pattern CONTROL = Pattern.compile("\\[([a-z_]+)]");

	/** The pad's name for each control while it is in use; null on the keyboard. */
	private static volatile Function<String, String> controls;

	/** Whether the player is on a controller, for the hint a screen builds in code rather than from a lang file. */
	public static boolean controller() {
		return controls != null;
	}

	/**
	 * The pad is in the player's hands, and {@code names} says what each control is called on it:
	 * a control's word in, the label to print out, or null for a word it does not know. Called
	 * again when a different pad takes over, since the names go with the pad.
	 */
	public static void controller(Function<String, String> names) {
		if (names == controls) return;
		controls = names;
		refresh();
	}

	/** The keys or the mouse are what the player is using now. */
	public static void keyboard() {
		if (controls == null) return;
		controls = null;
		refresh();
	}

	/** A {@code .controller} line with its controls named, or as written when there is no pad. */
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

	/**
	 * A translated component remembers its words until the language itself changes, so a hint on
	 * screen when the hands change would keep the old ones. Putting a fresh copy of the same
	 * language in place is what tells every component to look its words up again.
	 */
	private static void refresh() {
		Language current = Language.getInstance();
		if (current instanceof HintLanguage hints) Language.inject(new HintLanguage(hints.base()));
	}
}
