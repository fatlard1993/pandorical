package justfatlard.pandorical.notice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import justfatlard.pandorical.api.HudBuilder;
import justfatlard.pandorical.api.NoticeApi;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.ScreenApi;
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

	private static final int WIDTH = 260;
	private static final int ROW = 22;
	private static final int PAD = 8;
	private static final int ICON = 16;
	private static final int CHOICE = 20;

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
		PandoricalApi.keybinds().register(KEYBIND, justfatlard.pandorical.api.KeybindApi.letter('n'), "Open notices",
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

	private void badge(ServerPlayer player) {
		int count = waiting(player);
		if (count == 0) {
			PandoricalApi.hud().hide(player, BADGE);
			return;
		}
		String word = count == 1 ? "1 notice waiting" : count + " notices waiting";
		PandoricalApi.hud().show(player, new HudBuilder(BADGE)
			.anchor("top_right").offset(4, 4)
			.text("count", 0, 0, word)
			.build());
	}

	@Override
	public void open(ServerPlayer player) {
		openTray(player);
	}

	private void openTray(ServerPlayer player) {
		Map<String, Pending> mine = waiting.get(player.getUUID());
		List<Pending> rows;
		synchronized (mine == null ? this : mine) {
			rows = mine == null ? List.of() : List.copyOf(mine.values());
		}

		Map<String, Answer> buttons = new LinkedHashMap<>();
		int height = PAD * 2 + Math.max(ROW, rows.size() * (ROW + ROW));
		ScreenBuilder screen = new ScreenBuilder(TYPE).size(WIDTH, height).title("Notices");
		screen.panel("frame", 0, 0, WIDTH, height, Map.of());

		if (rows.isEmpty()) {
			screen.text("none", PAD, PAD, "Nothing is waiting for you");
		}

		int y = PAD;
		int n = 0;
		for (Pending each : rows) {
			Notice notice = each.notice();
			screen.itemIcon("icon" + n, PAD, y, notice.icon(), 1);
			screen.text("say" + n, PAD + ICON + 4, y + 4, notice.summary());
			y += ROW;

			int x = PAD + ICON + 4;
			int c = 0;
			for (Choice choice : notice.choices()) {
				String id = "b" + n + "_" + c;
				buttons.put(id, new Answer(notice.kind(), notice.id(), choice.id()));
				// The picture beside the button rather than on it. A button's own icon property is
				// a GUI atlas sprite, while a choice carries an item id - the same word the action
				// menus use - and handing one to the other draws the missing-texture chequer.
				screen.itemIcon("pic" + n + "_" + c, x, y + 2, choice.icon(), 1);
				screen.button(id, x + ICON + 2, y, CHOICE * 3, CHOICE, Map.of(
					justfatlard.pandorical.api.ComponentType.PROP_LABEL, choice.label()));
				x += ICON + 2 + CHOICE * 3 + 6;
				c++;
			}
			if (notice.choices().isEmpty()) {
				String id = "b" + n + "_seen";
				buttons.put(id, new Answer(notice.kind(), notice.id(), ""));
				screen.button(id, x + ICON + 2, y, CHOICE * 3, CHOICE, Map.of(
					justfatlard.pandorical.api.ComponentType.PROP_LABEL, "Dismiss"));
			}
			y += ROW;
			n++;
		}

		showing.put(player.getUUID(), buttons);
		PandoricalApi.screens().open(player, screen.build());
	}

	private void pressed(ServerPlayer player, String componentId) {
		Map<String, Answer> buttons = showing.get(player.getUUID());
		if (buttons == null || componentId == null) return;
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
