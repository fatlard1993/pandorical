package justfatlard.pandorical.form;

import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.BooleanInput;
import net.minecraft.server.dialog.input.InputControl;
import net.minecraft.server.dialog.input.NumberRangeInput;
import net.minecraft.server.dialog.input.SingleOptionInput;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A menu or a page of settings, sent as a vanilla dialog. A vanilla Java client draws it as a
 * screen, and Geyser turns it into a Bedrock form, so one builder reaches every player who has no
 * Pandorical to draw Pandorical's own screens.
 *
 * <p>Either buttons, each answering with its id, or fields with one submit button, answering with
 * every field's value. An answer is checked against what this form offered before
 * {@link Answer} sees it: an unknown button or field never arrives.</p>
 */
public final class SimpleForm {
	private static final int WIDTH = 200;

	/** {@code button} is the pressed button's id, or {@link #SUBMIT}; {@code values} are by field key. */
	public record Answer(ServerPlayer player, String button, Map<String, String> values) {}

	@FunctionalInterface
	public interface Handler {
		void answer(Answer answer);
	}

	public static final String SUBMIT = "submit";

	sealed interface Field permits Toggle, Choice, Number, Text {
		String key();
		String label();
		InputControl control();
		/** The answer's value as this field reads it, or null if it isn't one. */
		String read(Tag tag);
	}

	record Toggle(String key, String label, boolean initial) implements Field {
		public InputControl control() {
			return new BooleanInput(Component.literal(label), initial, "true", "false");
		}

		public String read(Tag tag) {
			Optional<Boolean> bool = tag.asBoolean();
			if (bool.isPresent()) return bool.get().toString();
			return tag.asString().filter(s -> s.equals("true") || s.equals("false")).orElse(null);
		}
	}

	record Choice(String key, String label, Map<String, String> options, String initial) implements Field {
		public InputControl control() {
			List<SingleOptionInput.Entry> entries = new ArrayList<>();
			options.forEach((id, shown) -> entries.add(new SingleOptionInput.Entry(id, Optional.of(Component.literal(shown)), id.equals(initial))));
			return new SingleOptionInput(WIDTH, entries, Component.literal(label), true);
		}

		public String read(Tag tag) {
			return tag.asString().filter(options::containsKey).orElse(null);
		}
	}

	record Number(String key, String label, int min, int max, int step, int initial) implements Field {
		public InputControl control() {
			return new NumberRangeInput(WIDTH, Component.literal(label), "options.generic_value",
				new NumberRangeInput.RangeInfo(min, max, Optional.of((float) initial), Optional.of((float) step)));
		}

		public String read(Tag tag) {
			return tag.asNumber().map(n -> n.floatValue())
				.filter(Float::isFinite)
				.map(Math::round)
				.filter(n -> n >= min && n <= max)
				.map(String::valueOf).orElse(null);
		}
	}

	record Text(String key, String label, String initial, int maxLength) implements Field {
		public InputControl control() {
			return new TextInput(WIDTH, Component.literal(label), true, initial, maxLength, Optional.empty());
		}

		public String read(Tag tag) {
			return tag.asString().filter(s -> s.length() <= maxLength).orElse(null);
		}
	}

	private final String title;
	private final List<String> body = new ArrayList<>();
	private final Map<String, String> buttons = new LinkedHashMap<>();
	private final Map<String, Field> fields = new LinkedHashMap<>();
	private String submitLabel = "Save";
	private String closeLabel = "Close";
	private Handler handler = answer -> {};
	/** Who it was shown to; a respawn makes a new one, and an answer from it goes to nobody. */
	private ServerPlayer shownTo;

	private SimpleForm(String title) {
		this.title = title;
	}

	public static SimpleForm titled(String title) {
		return new SimpleForm(title);
	}

	/** A paragraph of plain text above the buttons or fields. */
	public SimpleForm text(String paragraph) {
		if (paragraph != null && !paragraph.isBlank()) body.add(paragraph);
		return this;
	}

	public SimpleForm button(String id, String label) {
		buttons.put(id, label);
		return this;
	}

	public SimpleForm toggle(String key, String label, boolean initial) {
		return field(new Toggle(key, label, initial));
	}

	/** @param options value id to what the player sees, in order */
	public SimpleForm choice(String key, String label, Map<String, String> options, String initial) {
		return field(new Choice(key, label, new LinkedHashMap<>(options), initial));
	}

	public SimpleForm number(String key, String label, int min, int max, int step, int initial) {
		return field(new Number(key, label, min, max, Math.max(1, step), Math.clamp(initial, min, max)));
	}

	public SimpleForm text(String key, String label, String initial, int maxLength) {
		return field(new Text(key, label, initial == null ? "" : initial, maxLength));
	}

	public SimpleForm submit(String label) {
		this.submitLabel = label;
		return this;
	}

	/** The button that leaves without answering. */
	public SimpleForm close(String label) {
		this.closeLabel = label;
		return this;
	}

	public SimpleForm onAnswer(Handler handler) {
		this.handler = handler;
		return this;
	}

	private SimpleForm field(Field field) {
		fields.put(field.key(), field);
		return this;
	}

	/** Shows it, replacing whatever dialog the player had open; the id its answer comes back under. */
	public Identifier open(ServerPlayer player) {
		shownTo = player;
		Identifier id = Forms.remember(player, this);
		player.connection.send(new ClientboundShowDialogPacket(Holder.direct(dialog(id))));
		return id;
	}

	private Dialog dialog(Identifier id) {
		List<DialogBody> paragraphs = new ArrayList<>();
		for (String paragraph : body) paragraphs.add(new PlainMessage(Component.literal(paragraph), PlainMessage.DEFAULT_WIDTH));

		// Input keys reach a client and come back; Pandorical's own keys (mod:setting) aren't
		// valid there, so each field goes by its position.
		List<Input> inputs = new ArrayList<>();
		int index = 0;
		for (Field field : fields.values()) inputs.add(new Input(inputKey(index++), field.control()));

		CommonDialogData common = new CommonDialogData(Component.literal(title), Optional.empty(), true, false,
			DialogAction.CLOSE, paragraphs, inputs);
		ActionButton close = new ActionButton(new CommonButtonData(Component.literal(closeLabel), CommonButtonData.DEFAULT_WIDTH), Optional.empty());

		List<ActionButton> actions = new ArrayList<>();
		if (!fields.isEmpty()) {
			actions.add(new ActionButton(new CommonButtonData(Component.literal(submitLabel), CommonButtonData.DEFAULT_WIDTH),
				Optional.of(new CustomAll(id, Optional.of(pressed(SUBMIT))))));
		}
		buttons.forEach((buttonId, label) -> actions.add(new ActionButton(
			new CommonButtonData(Component.literal(label), CommonButtonData.DEFAULT_WIDTH),
			Optional.of(new StaticAction(new ClickEvent.Custom(id, Optional.of(pressed(buttonId))))))));

		if (actions.isEmpty()) return new NoticeDialog(common, close);
		return new MultiActionDialog(common, actions, Optional.of(close), 1);
	}

	private static CompoundTag pressed(String button) {
		CompoundTag tag = new CompoundTag();
		tag.putString(Forms.BUTTON_KEY, button);
		return tag;
	}

	private static String inputKey(int index) {
		return "f" + index;
	}

	/** The answer this form offered, or empty when the payload asks for something it didn't. */
	Optional<Answer> read(ServerPlayer player, Optional<Tag> payload) {
		// Handlers hold the player they were opened for, so an answer from a later body is refused.
		if (player != shownTo) return Optional.empty();
		if (!(payload.orElse(null) instanceof CompoundTag tag)) return Optional.empty();
		String button = tag.getStringOr(Forms.BUTTON_KEY, "");
		boolean submitted = button.equals(SUBMIT) && !fields.isEmpty();
		if (!submitted && !buttons.containsKey(button)) return Optional.empty();

		Map<String, String> values = new LinkedHashMap<>();
		if (submitted) {
			int index = 0;
			for (Field field : fields.values()) {
				Tag value = tag.get(inputKey(index++));
				String read = value == null ? null : field.read(value);
				if (read == null) return Optional.empty();
				values.put(field.key(), read);
			}
		}
		return Optional.of(new Answer(player, button, values));
	}

	void answer(Answer answer) {
		handler.answer(answer);
	}
}
