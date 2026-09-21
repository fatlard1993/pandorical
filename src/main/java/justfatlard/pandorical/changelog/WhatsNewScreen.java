package justfatlard.pandorical.changelog;

import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.ScreenApi;
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

	private static final String HEADING_COLOR = "#FFD8A0";
	private static final String NOTE_COLOR = "#C8C8C8";
	private static final String VERSION_COLOR = "#80B8FF";

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
		screen.panel("frame", 0, 0, WIDTH, HEIGHT, Map.of());

		List<Row> rows = new ArrayList<>();
		add(rows, changes, Changelog.Kind.ADDED, "New here");
		add(rows, changes, Changelog.Kind.UPDATED, "Changed");
		add(rows, changes, Changelog.Kind.REMOVED, "No longer here");

		if (rows.isEmpty()) {
			screen.text("none", PAD, PAD, "Nothing has changed since your last visit.");
		}

		int inner = WIDTH - PAD * 2;
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
			Changelog.Kind kind, String heading) {
		List<Changelog.Change> mine = changes.stream().filter(c -> c.kind() == kind).toList();
		if (mine.isEmpty()) return;
		rows.add(new Heading(heading));
		for (Changelog.Change change : mine) rows.add(new Entry(change));
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

	private record Entry(Changelog.Change change) implements Row {
		@Override
		public int height() {
			return ROW + Math.max(1, change.lines().size()) * LINE + 2;
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
			for (String line : change.lines()) {
				out.add(new justfatlard.pandorical.api.ComponentBuilder("note" + n + "_" + i++,
						justfatlard.pandorical.api.ComponentType.TEXT)
					.bounds(6, at, width - 6, LINE)
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
