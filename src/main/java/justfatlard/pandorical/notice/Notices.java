package justfatlard.pandorical.notice;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import justfatlard.pandorical.api.HudBuilder;
import justfatlard.pandorical.api.NoticeApi;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.ScreenApi;
import justfatlard.pandorical.settings.Glyphs;
import justfatlard.pandorical.settings.PlayerSettings;
import net.minecraft.server.MinecraftServer;
import justfatlard.pandorical.api.ComponentBuilder;
import justfatlard.pandorical.api.ComponentType;
import justfatlard.pandorical.keybind.KeybindPool;
import justfatlard.pandorical.api.ScreenBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The questions waiting for each player, and the tray they are answered in.
 *
 * <p>Kept on the server and drawn with the screens and HUD this mod already carries, rather than a
 * wire format of its own. That is not only less to write: it means a notice can be answered by any
 * client that can see a Pandorical screen, with nothing new installed and nothing to keep in step
 * across a version.
 */
public final class Notices implements NoticeApi {
	public static final Notices INSTANCE = new Notices();

	private Notices() {}

	private static final String TYPE = "pandorical:notices";
	private static final String BADGE = "pandorical:notices_badge";
	private static final String KEYBIND = "pandorical:notices";

	private static final int WIDTH = 300;
	private static final int ROW = 22;
	private static final int PAD = 8;
	private static final int ICON = 16;
	private static final int CHOICE = 20;
	/** Room for the heading above the first card. */
	private static final int HEADER = 14;
	private static final int CARD_GAP = 4;
	private static final int INSET = 6;
	private static final int CHOICE_WIDTH = 64;
	private static final int CHOICE_GAP = 6;
	private static final int LINE = 10;
	private static final int CLOCK_WIDTH = 28;
	private static final int CLOSE = 18;
	private static final int CLOSE_WIDTH = 70;
	private static final String CLOSE_ID = "close";
	private static final String FRAME_BACKGROUND = "#F0101010";
	private static final String FRAME_BORDER = "#FF4A4A4A";
	private static final String CARD_BACKGROUND = "#FF1E1E1E";
	private static final String CARD_BORDER = "#FF3A3A3A";
	private static final String TITLE_COLOR = "#FFFFFFFF";
	private static final String FAINT_COLOR = "#FF9A9A9A";

	/** Beyond this the tray stops being a tray; the oldest go to make room. */
	private static final int MOST = 8;

	private record Pending(Notice notice, long expiresAt) {}

	/** What a button in somebody's open tray answers, so no id has to be packed into a string. */
	private record Answer(String kind, String noticeId, String choiceId) {}

	/** Per player, in the order they arrived; the key is kind and id together. */
	private final Map<UUID, Map<String, Pending>> waiting = new ConcurrentHashMap<>();
	private final Map<UUID, Map<String, Answer>> showing = new ConcurrentHashMap<>();
	private final Map<String, ChoiceHandler> answered = new ConcurrentHashMap<>();
	private final Map<String, ExpiryHandler> expired = new ConcurrentHashMap<>();

	private static String key(String kind, String id) {
		return kind + "\u0000" + id;
	}

	/** Wired once, from Pandorical's own init. */
	public static void register() {
		// N, which is slot 2's own key, so this claims that slot every time rather than whichever
		// happened to be free. bindByDefault still matters for anyone already here: their client
		// has a stored binding for slot 2, and a stored "unbound" beats a changed default, so the
		// key is pushed once to the clients whose slot 2 is empty.
		PandoricalApi.keybinds().register(KEYBIND, justfatlard.pandorical.api.KeybindApi.letter('N'), "Open notices",
			INSTANCE::openTray);
		PandoricalApi.keybinds().bindByDefault(KEYBIND);
		PandoricalApi.screens().onActionFallback(TYPE, (player, data) ->
			INSTANCE.pressed(player, data.get(ScreenApi.FALLBACK_COMPONENT_ID_KEY)));
		PandoricalApi.screens().onClose(TYPE, player -> INSTANCE.showing.remove(player.getUUID()));
	}

	@Override
	public void offer(ServerPlayer player, Notice notice) {
		Map<String, Pending> mine = waiting.computeIfAbsent(player.getUUID(), id -> new LinkedHashMap<>());
		long until = notice.seconds() > 0 ? System.currentTimeMillis() + notice.seconds() * 1000L : 0L;
		// Collected inside the lock and told outside it, like every other handler call here.
		List<Pending> evicted = new ArrayList<>();
		synchronized (mine) {
			// Replaced rather than added: a second ask under one id is the same question again,
			// which is what stops one impatient player filling somebody's tray.
			mine.remove(key(notice.kind(), notice.id()));
			mine.put(key(notice.kind(), notice.id()), new Pending(notice, until));
			while (mine.size() > MOST) {
				String oldest = mine.keySet().iterator().next();
				Pending dropped = mine.remove(oldest);
				// Pushed out by newer questions, which is still a question that will never be
				// answered. The mod that asked hears about it the same way it hears about one
				// that timed out, or it waits for a reply that cannot come.
				if (dropped != null) evicted.add(dropped);
			}
		}
		for (Pending dropped : evicted) expire(player, dropped);
		// Quiet and short: a notice arriving should be noticed, not announced.
		player.level().playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(),
			SoundSource.PLAYERS, 0.4F, 1.6F);
		badge(player);
	}

	@Override
	public void withdraw(ServerPlayer player, String kind, String id) {
		Map<String, Pending> mine = waiting.get(player.getUUID());
		if (mine == null) return;
		synchronized (mine) {
			if (mine.remove(key(kind, id)) == null) return;
		}
		badge(player);
	}

	@Override
	public void withdrawAll(ServerPlayer player, String kind) {
		Map<String, Pending> mine = waiting.get(player.getUUID());
		if (mine == null) return;
		synchronized (mine) {
			if (!mine.keySet().removeIf(k -> k.startsWith(kind + "\u0000"))) return;
		}
		badge(player);
	}

	@Override
	public void onChoice(String kind, ChoiceHandler handler) {
		answered.put(kind, handler);
	}

	@Override
	public void onExpiry(String kind, ExpiryHandler handler) {
		expired.put(kind, handler);
	}

	@Override
	public int waiting(ServerPlayer player) {
		Map<String, Pending> mine = waiting.get(player.getUUID());
		return mine == null ? 0 : mine.size();
	}

	/**
	 * Tell whoever asked that a notice has gone without an answer.
	 *
	 * <p>Every way a notice can leave without being answered comes through here: running out of
	 * time, being pushed out by newer ones, and the player logging off. A mod that asked a question
	 * and is never told it died waits for a reply that is not coming.
	 */
	private void expire(ServerPlayer player, Pending gone) {
		ExpiryHandler handler = expired.get(gone.notice().kind());
		if (handler != null) handler.expired(player, gone.notice().id());
	}

	/** Once a second is often enough for something measured in whole seconds. */
	public static void tick(net.minecraft.server.MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) return;
		long now = System.currentTimeMillis();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Map<String, Pending> mine = INSTANCE.waiting.get(player.getUUID());
			if (mine == null || mine.isEmpty()) continue;
			List<Pending> gone = new ArrayList<>();
			synchronized (mine) {
				mine.values().removeIf(each -> {
					boolean over = each.expiresAt() > 0 && now >= each.expiresAt();
					if (over) gone.add(each);
					return over;
				});
			}
			for (Pending each : gone) INSTANCE.expire(player, each);
			if (!gone.isEmpty()) INSTANCE.badge(player);
		}
	}

	/** Forget a player who has gone, so a tray does not outlive the session it was asked in. */
	public static void forget(ServerPlayer player) {
		Map<String, Pending> mine = INSTANCE.waiting.remove(player.getUUID());
		INSTANCE.showing.remove(player.getUUID());
		if (mine == null) return;
		List<Pending> gone;
		synchronized (mine) {
			gone = new ArrayList<>(mine.values());
		}
		for (Pending each : gone) INSTANCE.expire(player, each);
	}

	/**
	 * Wide enough for the longest line either row can hold.
	 *
	 * <p>Nothing on this side can measure a string, so the width is chosen for the worst case and
	 * the text is kept inside it: "Set a key in Controls" is the longest hint, and it wants about
	 * a hundred pixels once the bell and the padding have taken theirs.
	 */
	private static final int BADGE_WIDTH = 140;
	private static final int BADGE_HEIGHT = 30;
	private static final int BADGE_ICON = 16;
	private static final String BADGE_BACKGROUND = "#E0141414";
	/** A style name, not a colour: the colour goes in PROP_BORDER_COLOR and only "flat" reads it. */
	private static final String BADGE_BORDER = "flat";
	private static final String BADGE_BORDER_COLOR = "#FFE0B23C";
	private static final String BADGE_TEXT = "#FFFFFFFF";
	private static final String BADGE_HINT = "#FFE0B23C";
	/**
	 * How far above the bottom the hotbar and its furniture reach: the bar itself, the experience
	 * bar over it, and the hearts and armour above that.
	 */
	private static final int HOTBAR_BAND = 52;
	/** The badge once the player knows what it is: one line, what it is about, and how many. */
	private static final int BADGE_SMALL_WIDTH = 156;
	private static final int BADGE_SMALL_HEIGHT = 22;
	/** Set on a player the first time they open the tray; while unset, the badge names the key. */
	private static final String OPENED = "pandorical:notices_opened";

	/**
	 * The corner card that says something is waiting, and how to open it.
	 *
	 * <p>It was one call to {@code HudBuilder.text}, which positions without sizing, so the line
	 * was drawn into a box of no width and clipped against the edge of the screen to a white
	 * sliver. What reached the player was a stray mark in the corner: not obviously a message, not
	 * obviously anything. It also never said how to open the tray, which left the one way in being
	 * a keybind nobody had been told about.
	 *
	 * <p>So: a panel with a border behind it, a bell beside it, and the player's own key named on
	 * the second line - read from what their client reports having bound, not from what the server
	 * would like it to be, because the two differ the moment somebody rebinds. When nothing is
	 * bound it says so and points at the controls screen, which is a dead end worth naming rather
	 * than a silence.
	 *
	 * <p>Bottom right, because the top right is the busiest corner of the screen: vanilla stacks
	 * its potion effects there, and in this suite the mail HUD and the fishing minigame are there
	 * too. A badge that has to be noticed cannot be the fourth thing in a pile.
	 *
	 * <p>Lifted clear of the hotbar rather than sat in the corner. The corner looks empty at the
	 * gui scale a screenshot is usually taken at, and is not: a large gui scale leaves the screen
	 * only a few hundred units across, the hotbar is a fixed 182 of them and centred, so it grows
	 * to meet anything pinned to the bottom right. Sitting a whole band above it costs nothing at
	 * any scale and collides at none.
	 */
	private void badge(ServerPlayer player) {
		int count = waiting(player);
		if (count == 0) {
			PandoricalApi.hud().hide(player, BADGE);
			return;
		}
		if (taught(player)) {
			small(player, count);
			return;
		}
		String word = count == 1 ? "1 notice waiting" : count + " notices waiting";
		PandoricalApi.hud().show(player, new HudBuilder(BADGE)
			.anchor("bottom_right").offset(4, HOTBAR_BAND)
			.component(new ComponentBuilder("card", ComponentType.PANEL)
				.bounds(0, 0, BADGE_WIDTH, BADGE_HEIGHT)
				.prop(ComponentType.PROP_BACKGROUND, BADGE_BACKGROUND)
				.prop(ComponentType.PROP_BORDER, BADGE_BORDER)
				.prop(ComponentType.PROP_BORDER_COLOR, BADGE_BORDER_COLOR)
				.build())
			.component(new ComponentBuilder("bell", ComponentType.ITEM_ICON)
				.bounds(PAD / 2, (BADGE_HEIGHT - BADGE_ICON) / 2, BADGE_ICON, BADGE_ICON)
				.prop(ComponentType.PROP_ITEM_ID, "minecraft:bell")
				.build())
			.component(new ComponentBuilder("count", ComponentType.TEXT)
				.bounds(PAD / 2 + BADGE_ICON + 4, 6, BADGE_WIDTH - BADGE_ICON - PAD, 9)
				.prop(ComponentType.PROP_TEXT, word)
				.prop(ComponentType.PROP_COLOR, BADGE_TEXT)
				.prop(ComponentType.PROP_SHADOW, "true")
				.build())
			.component(new ComponentBuilder("how", ComponentType.TEXT)
				.bounds(PAD / 2 + BADGE_ICON + 4, 17, BADGE_WIDTH - BADGE_ICON - PAD, 9)
				.prop(ComponentType.PROP_TEXT, howToOpen(player))
				.prop(ComponentType.PROP_COLOR, BADGE_HINT)
				.prop(ComponentType.PROP_SHADOW, "true")
				.build())
			.build());
	}

	/**
	 * The badge for somebody who has opened the tray before: a bell and a count, and nothing it
	 * has already told them.
	 *
	 * <p>The key is worth saying once and then not again. A hint that never stops is furniture:
	 * it takes the same room every time and is read the first time only, which is the worst of
	 * both - too big to ignore and too familiar to notice.
	 */
	private void small(ServerPlayer player, int count) {
		Pending newest = newest(player);
		// The newest notice's own picture, not a bell. A bell says something happened; an ender
		// pearl says somebody wants to teleport to you, and the whole point of glancing at a
		// corner is to learn whether it is worth stopping for. The one that just chimed is the
		// one being described, and the rest are counted after it.
		String icon = newest == null ? "minecraft:bell" : newest.notice().icon();
		String more = count > 1 ? "  +" + (count - 1) : "";
		int textLeft = 4 + BADGE_ICON + 4;
		int room = BADGE_SMALL_WIDTH - textLeft - 4 - Glyphs.width(more);
		String said = newest == null ? String.valueOf(count) : Glyphs.clip(newest.notice().summary(), room);

		PandoricalApi.hud().show(player, new HudBuilder(BADGE)
			.anchor("bottom_right").offset(4, HOTBAR_BAND)
			.component(new ComponentBuilder("card", ComponentType.PANEL)
				.bounds(0, 0, BADGE_SMALL_WIDTH, BADGE_SMALL_HEIGHT)
				.prop(ComponentType.PROP_BACKGROUND, BADGE_BACKGROUND)
				.prop(ComponentType.PROP_BORDER, BADGE_BORDER)
				.prop(ComponentType.PROP_BORDER_COLOR, BADGE_BORDER_COLOR)
				.build())
			.component(new ComponentBuilder("icon", ComponentType.ITEM_ICON)
				.bounds(4, (BADGE_SMALL_HEIGHT - BADGE_ICON) / 2, BADGE_ICON, BADGE_ICON)
				.prop(ComponentType.PROP_ITEM_ID, icon)
				.build())
			.component(new ComponentBuilder("said", ComponentType.TEXT)
				.bounds(textLeft, (BADGE_SMALL_HEIGHT - 9) / 2 + 1, room, 9)
				.prop(ComponentType.PROP_TEXT, said)
				.prop(ComponentType.PROP_COLOR, BADGE_TEXT)
				.prop(ComponentType.PROP_SHADOW, "true")
				.build())
			.component(new ComponentBuilder("more", ComponentType.TEXT)
				.bounds(BADGE_SMALL_WIDTH - 4 - Glyphs.width(more), (BADGE_SMALL_HEIGHT - 9) / 2 + 1,
					Glyphs.width(more), 9)
				.prop(ComponentType.PROP_TEXT, more)
				.prop(ComponentType.PROP_ALIGN, "right")
				.prop(ComponentType.PROP_COLOR, BADGE_HINT)
				.prop(ComponentType.PROP_SHADOW, "true")
				.build())
			.build());
	}

	/** The most recent question waiting on this player: the one the badge describes. */
	private @org.jspecify.annotations.Nullable Pending newest(ServerPlayer player) {
		Map<String, Pending> mine = waiting.get(player.getUUID());
		if (mine == null) return null;
		synchronized (mine) {
			Pending last = null;
			for (Pending each : mine.values()) last = each;
			return last;
		}
	}

	/**
	 * Whether this player has opened the tray before, kept in the world save beside their other
	 * settings so being taught once means once, not once per login.
	 */
	private static boolean taught(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null) return false;
		return PlayerSettings.get(server).get(player.getUUID(), OPENED) != null;
	}

	private static void remember(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null) return;
		PlayerSettings.get(server).put(player.getUUID(), OPENED, "1");
	}

	/** A letter key's name from its code, or null for anything that is not one. */
	private static @org.jspecify.annotations.Nullable String keyName(int code) {
		int letter = code - justfatlard.pandorical.api.KeybindApi.letter('A');
		return letter >= 0 && letter < 26 ? String.valueOf((char) ('A' + letter)) : null;
	}

	/**
	 * What this player presses to open the tray, in their own words.
	 *
	 * <p>Asked of the client's reported binding rather than the preferred default: slots are
	 * handed out in registration order, so the key that opens this is whatever their client says
	 * it is, and a player who rebound it should be told what they chose.
	 */
	private static String howToOpen(ServerPlayer player) {
		for (KeybindPool.Claim claim : KeybindPool.INSTANCE.claims()) {
			if (!claim.id().equals(KEYBIND)) continue;
			String bound = KeybindPool.INSTANCE.bindingOf(player, claim.slot());
			if (bound != null && !bound.isEmpty()) return "Press " + bound;
			// Null is a client that has not said yet; empty is one that has said "nothing bound".
			// Treating them alike told players with a perfectly good key that they had none,
			// because a client only re-reports its bindings when applying a default changed one,
			// and a client that already had the key changed nothing. Where the slot ships with a
			// key, that key is the honest answer until the client contradicts it.
			if (bound == null) {
				String byDefault = keyName(KeybindPool.poolDefaultKey(claim.slot()));
				if (byDefault != null) return "Press " + byDefault;
			}
			// Short enough to fit the card. The card cannot grow to its text - nothing here can
			// measure a string - so the text is what gives.
			return "Set a key in Controls";
		}
		return "See the Pandorical menu";
	}

	@Override
	public void open(ServerPlayer player) {
		openTray(player);
	}

	private void openTray(ServerPlayer player) {
		// Finding it once is the whole of what the hint was for.
		remember(player);
		Map<String, Pending> mine = waiting.get(player.getUUID());
		List<Pending> rows;
		synchronized (mine == null ? this : mine) {
			rows = mine == null ? List.of() : List.copyOf(mine.values());
		}

		int cardWidth = WIDTH - PAD * 2;
		// Measured up front: a card is as tall as its summary needs, so the heights have to be
		// known before the first one is placed.
		List<List<String>> saidAs = new ArrayList<>();
		List<Integer> heights = new ArrayList<>();
		for (Pending each : rows) {
			List<String> lines = Glyphs.wrap(each.notice().summary(), cardWidth - INSET * 2 - ICON - 6 - CLOCK_WIDTH);
			saidAs.add(lines);
			heights.add(cardHeight(lines.size(), each.notice().choices().size()));
		}

		Map<String, Answer> buttons = new LinkedHashMap<>();
		int body = 0;
		for (int h : heights) body += h + CARD_GAP;
		if (rows.isEmpty()) body = ROW + CARD_GAP;
		int height = PAD + HEADER + body + CLOSE + PAD;

		ScreenBuilder screen = new ScreenBuilder(TYPE).size(WIDTH, height).title("Notices");
		screen.component(new ComponentBuilder("frame", ComponentType.PANEL)
			.bounds(0, 0, WIDTH, height)
			.prop(ComponentType.PROP_BACKGROUND, FRAME_BACKGROUND)
			.prop(ComponentType.PROP_BORDER, "flat")
			.prop(ComponentType.PROP_BORDER_COLOR, FRAME_BORDER));
		// A ScreenBuilder panel draws no heading of its own - the vanilla screen title sits above
		// where this panel is, which on a panel this small is off the top of it - so the heading
		// is a component like everything else.
		screen.component(new ComponentBuilder("title", ComponentType.TEXT)
			.bounds(PAD, PAD, cardWidth, 9)
			.prop(ComponentType.PROP_TEXT, rows.isEmpty() ? "Notices"
				: rows.size() == 1 ? "1 notice waiting" : rows.size() + " notices waiting")
			.prop(ComponentType.PROP_ALIGN, "center")
			.prop(ComponentType.PROP_COLOR, TITLE_COLOR)
			.prop(ComponentType.PROP_SHADOW, "true"));

		int y = PAD + HEADER;
		if (rows.isEmpty()) {
			screen.component(new ComponentBuilder("none", ComponentType.TEXT)
				.bounds(PAD, y + 4, cardWidth, 9)
				.prop(ComponentType.PROP_TEXT, "Nothing is waiting for you")
				.prop(ComponentType.PROP_ALIGN, "center")
				.prop(ComponentType.PROP_COLOR, FAINT_COLOR));
		}

		long now = System.currentTimeMillis();
		for (int n = 0; n < rows.size(); n++) {
			Pending each = rows.get(n);
			Notice notice = each.notice();
			List<String> lines = saidAs.get(n);
			int cardH = heights.get(n);

			// One card per question, so two of them read as two things to answer rather than as
			// four lines in a row.
			screen.component(new ComponentBuilder("card" + n, ComponentType.PANEL)
				.bounds(PAD, y, cardWidth, cardH)
				.prop(ComponentType.PROP_BACKGROUND, CARD_BACKGROUND)
				.prop(ComponentType.PROP_BORDER, "flat")
				.prop(ComponentType.PROP_BORDER_COLOR, CARD_BORDER));
			screen.itemIcon("icon" + n, PAD + INSET, y + INSET, notice.icon(), 1);

			int textX = PAD + INSET + ICON + 6;
			int textWidth = cardWidth - INSET * 2 - ICON - 6 - CLOCK_WIDTH;
			for (int i = 0; i < lines.size(); i++) {
				screen.component(new ComponentBuilder("say" + n + "_" + i, ComponentType.TEXT)
					.bounds(textX, y + INSET + 4 + i * LINE, textWidth, 9)
					.prop(ComponentType.PROP_TEXT, lines.get(i))
					.prop(ComponentType.PROP_COLOR, TITLE_COLOR)
					.prop(ComponentType.PROP_SHADOW, "true"));
			}

			// The clock, where there is one, sits at the far end of the first line, so a question
			// about to expire says so before it is read rather than after.
			if (each.expiresAt() > 0) {
				screen.component(new ComponentBuilder("clock" + n, ComponentType.TEXT)
					.bounds(PAD + cardWidth - INSET - CLOCK_WIDTH, y + INSET + 4, CLOCK_WIDTH, 9)
					.prop(ComponentType.PROP_TEXT, Math.max(0, (each.expiresAt() - now) / 1000L) + "s")
					.prop(ComponentType.PROP_ALIGN, "right")
					.prop(ComponentType.PROP_COLOR, FAINT_COLOR));
			}

			// Answers run from the right edge inwards, so the buttons line up with each other and
			// with the card down the one edge every card shares. Laid out left to right from a
			// computed start rather than right to left, so each choice's picture stays on its own
			// button's left rather than drifting onto its neighbour.
			List<Choice> choices = notice.choices();
			int answers = Math.max(1, choices.size());
			int groupWidth = answers * (ICON + CHOICE_WIDTH) + (answers - 1) * CHOICE_GAP;
			int x = PAD + cardWidth - INSET - groupWidth;
			int buttonsY = y + cardH - INSET - CHOICE;
			for (int c = 0; c < choices.size(); c++) {
				Choice choice = choices.get(c);
				String id = "b" + n + "_" + c;
				buttons.put(id, new Answer(notice.kind(), notice.id(), choice.id()));
				// The picture beside the button rather than on it. A button's own icon property is
				// a GUI atlas sprite, while a choice carries an item id - the same word the action
				// menus use - and handing one to the other draws the missing-texture chequer.
				// Flush against its button, not floating near it: with a gap they read as a
				// picture and then a button rather than as one thing to press.
				screen.itemIcon("pic" + n + "_" + c, x, buttonsY + 2, choice.icon(), 1);
				screen.button(id, x + ICON, buttonsY, CHOICE_WIDTH, CHOICE, Map.of(
					ComponentType.PROP_LABEL, choice.label()));
				x += ICON + CHOICE_WIDTH + CHOICE_GAP;
			}
			if (choices.isEmpty()) {
				String id = "b" + n + "_seen";
				buttons.put(id, new Answer(notice.kind(), notice.id(), ""));
				screen.button(id, x + ICON, buttonsY, CHOICE_WIDTH, CHOICE, Map.of(
					ComponentType.PROP_LABEL, "Dismiss"));
			}
			y += cardH + CARD_GAP;
		}

		// Leaving without answering is a real answer to "later", and a tray with no way out but
		// Escape is one the player has to guess their way out of.
		screen.button(CLOSE_ID, (WIDTH - CLOSE_WIDTH) / 2, height - PAD - CLOSE, CLOSE_WIDTH, CLOSE,
			// Always "Close", never a word a notice might also put on one of its own answers:
			// a mail notice offering "Later" beside a tray button saying "Later" is two different
			// things wearing one word.
			Map.of(ComponentType.PROP_LABEL, "Close"));

		showing.put(player.getUUID(), buttons);
		PandoricalApi.screens().open(player, screen.build());
	}

	/** A card is its summary, however many lines that took, and a row of answers under it. */
	private static int cardHeight(int lines, int choices) {
		return INSET + Math.max(ICON, lines * LINE) + 6 + CHOICE + INSET;
	}

	private void pressed(ServerPlayer player, String componentId) {
		Map<String, Answer> buttons = showing.get(player.getUUID());
		if (buttons == null || componentId == null) return;
		if (CLOSE_ID.equals(componentId)) {
			PandoricalApi.screens().close(player, TYPE);
			return;
		}
		Answer answer = buttons.get(componentId);
		if (answer == null) return;

		// Taken away before the handler runs: answering is what spends a notice, and a handler that
		// offers a fresh one in reply must not have it swept up afterwards.
		withdraw(player, answer.kind(), answer.noticeId());

		ChoiceHandler handler = answered.get(answer.kind());
		if (handler != null && !answer.choiceId().isEmpty()) {
			handler.answered(player, answer.noticeId(), answer.choiceId());
		}

		PandoricalApi.screens().close(player, TYPE);
		if (waiting(player) > 0) openTray(player);
	}
}
