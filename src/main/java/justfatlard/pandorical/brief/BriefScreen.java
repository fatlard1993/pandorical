package justfatlard.pandorical.brief;

import justfatlard.pandorical.api.ComponentBuilder;
import justfatlard.pandorical.api.ComponentType;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.ScreenApi;
import justfatlard.pandorical.api.ScreenBuilder;
import justfatlard.pandorical.protocol.ComponentDef;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The brief: every mod on this server in a line, and two ways out of it.
 *
 * <p>Overviews only. A new player has no idea yet which of forty mods they will care about, so
 * ranking them for them is guessing, and a readme each is a wall. One pass, then the choice: put it
 * down, or open the mods screen, which is where the readmes, settings and commands already live.
 *
 * <p>That ending is the whole design. The brief does not try to be the documentation; it is the
 * thing that tells somebody the documentation is there.
 */
public final class BriefScreen {
	private BriefScreen() {}

	private static final String TYPE = "pandorical:brief_screen";

	private static final int WIDTH = 300;
	private static final int HEIGHT = 200;
	private static final int PAD = 8;
	private static final int LINE = 10;
	private static final int ROW = 14;

	private static final String NAME_COLOR = "#FFD8A0";
	private static final String LINE_COLOR = "#C8C8C8";

	static void register() {
		PandoricalApi.screens().onActionFallback(TYPE, (player, data) -> {
			String pressed = data.get(ScreenApi.FALLBACK_COMPONENT_ID_KEY);
			if ("done".equals(pressed)) {
				PandoricalApi.screens().close(player, TYPE);
			} else if ("mods".equals(pressed)) {
				// Closed first: the mods screen is a screen too, and opening one over another
				// leaves the player a close away from a screen they thought they had left.
				PandoricalApi.screens().close(player, TYPE);
				PandoricalApi.settings().open(player);
			}
		});
	}

	public static void open(ServerPlayer player) {
		List<Brief.Entry> entries = Brief.INSTANCE.entries();

		ScreenBuilder screen = new ScreenBuilder(TYPE).size(WIDTH, HEIGHT).title("What is running here");
		screen.panel("frame", 0, 0, WIDTH, HEIGHT, Map.of());

		int inner = WIDTH - PAD * 2;
		List<ComponentDef> body = new ArrayList<>();
		int y = 0;
		int n = 0;
		for (Brief.Entry entry : entries) {
			body.add(new ComponentBuilder("name" + n, ComponentType.TEXT)
				.bounds(0, y, inner, LINE)
				.prop(ComponentType.PROP_TEXT, entry.name())
				.prop(ComponentType.PROP_COLOR, NAME_COLOR).build());
			y += LINE;
			int i = 0;
			for (String line : entry.lines()) {
				body.add(new ComponentBuilder("line" + n + "_" + i++, ComponentType.TEXT)
					.bounds(6, y, inner - 6, LINE)
					.prop(ComponentType.PROP_TEXT, line)
					.prop(ComponentType.PROP_COLOR, LINE_COLOR).build());
				y += LINE;
			}
			y += 4;
			n++;
		}

		int listH = HEIGHT - PAD * 2 - 24;
		screen.scrollPanel("brief", PAD, PAD, inner, listH, Map.of(
			"item_height", String.valueOf(ROW),
			"visible_items", String.valueOf(listH / ROW),
			// Measured off what was laid out rather than counted in mods: the lines under each
			// name are not rows, and counting names would put the last mod below the floor.
			"total_items", String.valueOf((y + ROW - 1) / ROW),
			"scroll_offset", "0",
			"show_scrollbar", String.valueOf(y > listH)), body);

		int buttons = HEIGHT - PAD - 16;
		screen.button("done", PAD, buttons, 100, 16,
			Map.of(ComponentType.PROP_LABEL, "Close"));
		screen.button("mods", WIDTH - PAD - 150, buttons, 150, 16,
			Map.of(ComponentType.PROP_LABEL, "Read more in the mods menu"));

		PandoricalApi.screens().open(player, screen.build());
	}
}
