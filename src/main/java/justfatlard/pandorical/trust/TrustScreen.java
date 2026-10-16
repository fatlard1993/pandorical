package justfatlard.pandorical.trust;

import justfatlard.pandorical.api.ComponentBuilder;
import justfatlard.pandorical.api.ComponentType;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.ScreenApi;
import justfatlard.pandorical.api.ScreenBuilder;
import justfatlard.pandorical.api.Trust;
import justfatlard.pandorical.form.SimpleForm;
import justfatlard.pandorical.protocol.ComponentDef;
import justfatlard.pandorical.settings.SettingsRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The page ops set trust on: everyone's defaults on the first row, then a row for every player the
 * server has seen, online first, each switch a button that steps through default, off and on.
 *
 * <p>PvP's default is the game's own gamerule, so its button on the first row is the gamerule: one
 * place to see and set it, not a second setting that could disagree with the first.
 */
public final class TrustScreen {
	private TrustScreen() {}

	public static final String TYPE = "pandorical:trust";

	private static final int WIDTH = 420;
	private static final int HEIGHT = 240;
	private static final int PAD = 8;
	private static final int ROW = 20;
	private static final int FACE = 16;
	private static final int NAME_W = 110;
	private static final int SWITCH_W = 66;
	private static final int SWITCH_GAP = 4;
	private static final String FRAME_BACKGROUND = "#F0101010";
	private static final String FRAME_BORDER = "#FF4A4A4A";
	private static final String DIM = "#FF9A9A9A";

	public static void register() {
		PandoricalApi.screens().onActionFallback(TYPE, (player, data) -> {
			if (!SettingsRegistry.isOp(player)) return;
			String pressed = data.get(ScreenApi.FALLBACK_COMPONENT_ID_KEY);
			if (pressed == null) return;
			if (pressed.equals("done")) {
				PandoricalApi.screens().close(player, TYPE);
				return;
			}
			String[] parts = pressed.split(":");
			MinecraftServer server = player.level().getServer();
			TrustBook book = TrustBook.get(server);
			if (parts.length == 2 && parts[0].equals("d")) {
				Trust what = Trust.byId(parts[1]);
				if (what == Trust.PVP) {
					server.overworld().getGameRules().set(GameRules.PVP, !server.overworld().getGameRules().get(GameRules.PVP), server);
				} else if (what != null) {
					book.chooseDefault(what, !book.byDefault(what));
				}
			} else if (parts.length == 3 && parts[0].equals("p")) {
				UUID who = UUID.fromString(parts[1]);
				Trust what = Trust.byId(parts[2]);
				if (what != null) book.choose(who, what, next(book.choice(who, what)));
			}
			open(player);
		});
	}

	/** The same, as forms, for an op whose client can't draw Pandorical's screens. */
	public static void openForm(ServerPlayer op) {
		if (!SettingsRegistry.isOp(op)) return;
		MinecraftServer server = op.level().getServer();
		TrustBook book = TrustBook.get(server);
		List<UUID> players = sortedPlayers(server, book);
		SimpleForm form = SimpleForm.titled("Who may do what").button("everyone", "Everyone, by default");
		for (UUID who : players) {
			boolean online = server.getPlayerList().getPlayer(who) != null;
			form.button(who.toString(), book.known().get(who) + (online ? "" : " (away)"));
		}
		form.onAnswer(answer -> {
			if (!SettingsRegistry.isOp(op)) return;
			if (answer.button().equals("everyone")) {
				everyoneForm(op);
			} else {
				UUID who = UUID.fromString(answer.button());
				if (book.known().containsKey(who)) playerForm(op, who);
			}
		}).open(op);
	}

	private static void everyoneForm(ServerPlayer op) {
		MinecraftServer server = op.level().getServer();
		SimpleForm form = SimpleForm.titled("Everyone, by default")
			.text("For anyone not set by name. PvP is the game's pvp gamerule.");
		for (Trust what : Trust.values()) form.toggle(what.id(), what.label, TrustRules.byDefault(server.overworld(), what));
		form.button("back", "Back").onAnswer(answer -> {
			if (!SettingsRegistry.isOp(op)) return;
			if (answer.button().equals("back")) {
				openForm(op);
				return;
			}
			TrustBook book = TrustBook.get(server);
			for (Trust what : Trust.values()) {
				boolean on = Boolean.parseBoolean(answer.values().get(what.id()));
				if (on == TrustRules.byDefault(server.overworld(), what)) continue;
				if (what == Trust.PVP) server.overworld().getGameRules().set(GameRules.PVP, on, server);
				else book.chooseDefault(what, on);
			}
			op.sendSystemMessage(Component.literal("Saved the defaults."));
		}).open(op);
	}

	private static void playerForm(ServerPlayer op, UUID who) {
		MinecraftServer server = op.level().getServer();
		TrustBook book = TrustBook.get(server);
		String name = book.known().get(who);
		SimpleForm form = SimpleForm.titled(name);
		for (Trust what : Trust.values()) {
			Map<String, String> options = new LinkedHashMap<>();
			options.put("default", "Default (" + (TrustRules.byDefault(server.overworld(), what) ? "on" : "off") + ")");
			options.put("on", "On");
			options.put("off", "Off");
			Boolean own = book.choice(who, what);
			form.choice(what.id(), what.label, options, own == null ? "default" : own ? "on" : "off");
		}
		form.button("back", "Back").onAnswer(answer -> {
			if (!SettingsRegistry.isOp(op)) return;
			if (answer.button().equals("back")) {
				openForm(op);
				return;
			}
			for (Trust what : Trust.values()) {
				Boolean chosen = switch (answer.values().get(what.id())) {
					case "on" -> true;
					case "off" -> false;
					default -> null;
				};
				book.choose(who, what, chosen);
			}
			op.sendSystemMessage(Component.literal("Saved " + name + "'s trust."));
		}).open(op);
	}

	/** Online first, then by name. */
	private static List<UUID> sortedPlayers(MinecraftServer server, TrustBook book) {
		List<UUID> players = new ArrayList<>(book.known().keySet());
		players.sort(Comparator
			.comparing((UUID id) -> server.getPlayerList().getPlayer(id) == null)
			.thenComparing(id -> book.known().get(id).toLowerCase()));
		return players;
	}

	/** Default, then off, then on, then back to default. */
	private static Boolean next(Boolean current) {
		if (current == null) return false;
		return current ? null : true;
	}

	public static void open(ServerPlayer op) {
		MinecraftServer server = op.level().getServer();
		TrustBook book = TrustBook.get(server);

		ScreenBuilder screen = new ScreenBuilder(TYPE).size(WIDTH, HEIGHT).title("Who may do what");
		screen.panel("frame", 0, 0, WIDTH, HEIGHT, Map.of(
			ComponentType.PROP_BACKGROUND, FRAME_BACKGROUND,
			ComponentType.PROP_BORDER, "flat",
			ComponentType.PROP_BORDER_COLOR, FRAME_BORDER));

		int columns = PAD + FACE + 4 + NAME_W;
		Trust[] all = Trust.values();
		for (int i = 0; i < all.length; i++) {
			screen.component(new ComponentBuilder("head" + i, ComponentType.TEXT)
				.bounds(columns + i * (SWITCH_W + SWITCH_GAP), PAD, SWITCH_W, 10)
				.prop(ComponentType.PROP_TEXT, all[i].label)
				.prop(ComponentType.PROP_TOOLTIP, all[i].description));
		}

		int top = PAD + 14;
		screen.component(new ComponentBuilder("everyone", ComponentType.TEXT)
			.bounds(PAD, top + 5, FACE + 4 + NAME_W, 10)
			.prop(ComponentType.PROP_TEXT, "Everyone, by default"));
		for (int i = 0; i < all.length; i++) {
			Trust what = all[i];
			boolean on = TrustRules.byDefault(server.overworld(), what);
			screen.button("d:" + what.id(), columns + i * (SWITCH_W + SWITCH_GAP), top, SWITCH_W, 16, Map.of(
				ComponentType.PROP_LABEL, on ? "on" : "off",
				ComponentType.PROP_TOOLTIP, what == Trust.PVP ? "The game's pvp gamerule" : "Anyone not set below"));
		}

		List<UUID> players = sortedPlayers(server, book);

		List<ComponentDef> rows = new ArrayList<>();
		int y = 0;
		for (UUID who : players) {
			boolean online = server.getPlayerList().getPlayer(who) != null;
			rows.add(new ComponentBuilder("face:" + who, ComponentType.PLAYER_FACE)
				.bounds(0, y + 2, FACE, FACE)
				.prop(ComponentType.PROP_PLAYER, who.toString()).build());
			ComponentBuilder name = new ComponentBuilder("name:" + who, ComponentType.TEXT)
				.bounds(FACE + 4, y + 6, NAME_W, 10)
				.prop(ComponentType.PROP_TEXT, book.known().get(who));
			if (!online) name.prop(ComponentType.PROP_COLOR, DIM);
			rows.add(name.build());
			for (int i = 0; i < all.length; i++) {
				Boolean own = book.choice(who, all[i]);
				String label = own == null
					? "default (" + (TrustRules.byDefault(server.overworld(), all[i]) ? "on" : "off") + ")"
					: own ? "on" : "off";
				rows.add(new ComponentBuilder("p:" + who + ":" + all[i].id(), ComponentType.BUTTON)
					.bounds(columns - PAD + i * (SWITCH_W + SWITCH_GAP), y + 2, SWITCH_W, 16)
					.prop(ComponentType.PROP_LABEL, label).build());
			}
			y += ROW;
		}

		int listTop = top + 24;
		int listH = HEIGHT - listTop - PAD - 22;
		screen.scrollPanel("players", PAD, listTop, WIDTH - PAD * 2, listH, Map.of(
			"item_height", String.valueOf(ROW),
			"visible_items", String.valueOf(listH / ROW),
			"total_items", String.valueOf(players.size()),
			"scroll_offset", "0",
			"show_scrollbar", String.valueOf(players.size() * ROW > listH)), rows);

		screen.button("done", WIDTH - PAD - 80, HEIGHT - PAD - 16, 80, 16, Map.of(ComponentType.PROP_LABEL, "Done"));
		PandoricalApi.screens().open(op, screen.build());
	}
}
