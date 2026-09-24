package justfatlard.pandorical.changelog;

import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.ScreenApi;
import justfatlard.pandorical.settings.Glyphs;
import justfatlard.pandorical.api.ScreenBuilder;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What changed while you were away, grouped into what arrived, what moved on and what is gone.
 *
 * <p>Its own screen rather than the mods screen with marks on it: the mods screen answers "what is
 * running here", and this answers "what is different", which is a list that exists for one reading
 * and then never again. Marking up the other one would leave every later visit carrying badges
 * about a visit already finished.
 *
 * <p>What a mod says about itself is shown under its name. A mod with nothing to say is still
 * listed, by the versions it moved between, so a change nobody wrote up is still a change the
 * player is told about.
 */
final class WhatsNewScreen {
	private WhatsNewScreen() {}

	private static final String TYPE = "pandorical:whats_new_screen";

	private static final int WIDTH = 300;
	private static final int HEIGHT = 200;
	private static final int PAD = 8;
	private static final int LINE = 10;
	private static final int ROW = 14;
	private static final int ICON = 16;
	/** How far a note is indented under its mod name. */
	private static final int NOTE_INSET = 6;

	// Eight digits, alpha first, like every other colour here. And on a dark frame, because the
	// notes were light grey on the light grey a bare panel draws: the headings and the version
	// numbers were tinted enough to read, and the actual words of what changed were grey on grey.
	// A "what's new" screen nobody can read the "what" of is the same as not writing one.
	private static final String FRAME_BACKGROUND = "#F0101010";
	private static final String FRAME_BORDER = "#FF4A4A4A";
	private static final String HEADING_COLOR = "#FFFFD8A0";
	private static final String NOTE_COLOR = "#FFD8D8D8";
	private static final String VERSION_COLOR = "#FF80B8FF";

	static void register() {
		PandoricalApi.screens().onActionFallback(TYPE, (player, data) -> {
			if ("done".equals(data.get(ScreenApi.FALLBACK_COMPONENT_ID_KEY))) {
				PandoricalApi.screens().close(player, TYPE);
			}
		});
	}

	static void open(ServerPlayer player) {
		List<Changelog.Change> changes = Changelog.INSTANCE.pendingFor(player);

		ScreenBuilder screen = new ScreenBuilder(TYPE).size(WIDTH, HEIGHT).title("Since you were away");
		screen.panel("frame", 0, 0, WIDTH, HEIGHT, Map.of(
			justfatlard.pandorical.api.ComponentType.PROP_BACKGROUND, FRAME_BACKGROUND,
			justfatlard.pandorical.api.ComponentType.PROP_BORDER, "flat",
			justfatlard.pandorical.api.ComponentType.PROP_BORDER_COLOR, FRAME_BORDER));

		int inner = WIDTH - PAD * 2;
		List<Row> rows = new ArrayList<>();
		add(rows, changes, Changelog.Kind.ADDED, "New here", inner);
		add(rows, changes, Changelog.Kind.UPDATED, "Changed", inner);
		add(rows, changes, Changelog.Kind.REMOVED, "No longer here", inner);

		if (rows.isEmpty()) {
			screen.text("none", PAD, PAD, "Nothing has changed since your last visit.");
		}

		int height = 0;
		for (Row row : rows) height += row.height();

		List<justfatlard.pandorical.protocol.ComponentDef> body = new ArrayList<>();
		int y = 0;
		int n = 0;
		for (Row row : rows) {
			row.draw(body, n++, y, inner);
			y += row.height();
		}

		int listH = HEIGHT - PAD * 2 - 24;
		screen.scrollPanel("changes", PAD, PAD, inner, listH, Map.of(
			"item_height", String.valueOf(ROW),
			"visible_items", String.valueOf(listH / ROW),
			// Measured off what was actually laid out, so a heading or a wrapped note cannot leave
			// the last mod below the floor.
			"total_items", String.valueOf((height + ROW - 1) / ROW),
			"scroll_offset", "0",
			"show_scrollbar", String.valueOf(height > listH)), body);

		screen.button("done", WIDTH / 2 - 40, HEIGHT - PAD - 16, 80, 16,
			Map.of(justfatlard.pandorical.api.ComponentType.PROP_LABEL, "Done"));

		PandoricalApi.screens().open(player, screen.build());
	}

	private static void add(List<Row> rows, List<Changelog.Change> changes,
			Changelog.Kind kind, String heading, int width) {
		List<Changelog.Change> mine = changes.stream().filter(c -> c.kind() == kind).toList();
		if (mine.isEmpty()) return;
		rows.add(new Heading(heading));
		for (Changelog.Change change : mine) rows.add(Entry.of(change, width));
	}

	private interface Row {
		int height();
		void draw(List<justfatlard.pandorical.protocol.ComponentDef> out, int n, int y, int width);
	}

	private record Heading(String text) implements Row {
		@Override public int height() { return ROW + 4; }

		@Override
		public void draw(List<justfatlard.pandorical.protocol.ComponentDef> out, int n, int y, int width) {
			out.add(new justfatlard.pandorical.api.ComponentBuilder("head" + n,
					justfatlard.pandorical.api.ComponentType.TEXT)
				.bounds(0, y + 4, width, LINE)
				.prop(justfatlard.pandorical.api.ComponentType.PROP_TEXT, text)
				.prop(justfatlard.pandorical.api.ComponentType.PROP_COLOR, HEADING_COLOR).build());
		}
	}

	/**
	 * @param wrapped the note's lines broken to the width they are drawn in, worked out once so
	 *     {@link #height()} and {@link #draw} cannot disagree about how tall the entry is. They
	 *     were drawn unwrapped, one component per note, each clipped at the panel edge - so every
	 *     note read as its first few words and a hard stop.
	 */
	private record Entry(Changelog.Change change, List<String> wrapped) implements Row {
		static Entry of(Changelog.Change change, int width) {
			List<String> wrapped = new ArrayList<>();
			for (String line : change.lines()) wrapped.addAll(Glyphs.wrap(line, width - NOTE_INSET));
			if (wrapped.isEmpty()) wrapped.add("");
			return new Entry(change, wrapped);
		}

		@Override
		public int height() {
			return ROW + Math.max(1, wrapped.size()) * LINE + 2;
		}

		@Override
		public void draw(List<justfatlard.pandorical.protocol.ComponentDef> out, int n, int y, int width) {
			out.add(new justfatlard.pandorical.api.ComponentBuilder("name" + n,
					justfatlard.pandorical.api.ComponentType.TEXT)
				.bounds(0, y, width, LINE)
				.prop(justfatlard.pandorical.api.ComponentType.PROP_TEXT, change.name())
				.build());
			out.add(new justfatlard.pandorical.api.ComponentBuilder("ver" + n,
					justfatlard.pandorical.api.ComponentType.TEXT)
				.bounds(0, y + LINE, width, LINE)
				.prop(justfatlard.pandorical.api.ComponentType.PROP_TEXT, versions())
				.prop(justfatlard.pandorical.api.ComponentType.PROP_COLOR, VERSION_COLOR).build());

			int at = y + LINE * 2;
			int i = 0;
			for (String line : wrapped) {
				out.add(new justfatlard.pandorical.api.ComponentBuilder("note" + n + "_" + i++,
						justfatlard.pandorical.api.ComponentType.TEXT)
					.bounds(NOTE_INSET, at, width - NOTE_INSET, LINE)
					.prop(justfatlard.pandorical.api.ComponentType.PROP_TEXT, line)
					.prop(justfatlard.pandorical.api.ComponentType.PROP_COLOR, NOTE_COLOR).build());
				at += LINE;
			}
		}

		/** The versions, which is all there is to say when the mod said nothing. */
		private String versions() {
			return switch (change.kind()) {
				case ADDED -> change.to();
				case REMOVED -> "was " + change.from();
				case UPDATED -> change.from() + " to " + change.to();
			};
		}
	}
}
