package justfatlard.pandorical.brief;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.api.BriefApi;
import justfatlard.pandorical.api.NoticeApi;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.settings.ModCatalog;
import justfatlard.pandorical.settings.PlayerSettings;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One pass over what this server runs, for a player on their first visit.
 *
 * <p>A new player is told nothing about forty mods and finds them by walking into them, which works
 * for a block that looks interesting and not at all for a command or a rule. This is the overview,
 * offered once, and it ends by pointing at the mods screen for anyone who wants the rest.
 *
 * <p>Offered rather than opened. Somebody's first minute on a server is theirs, and a screen that
 * takes it without asking is worse than no orientation at all: it waits in the tray like any other
 * notice until they have a moment.
 *
 * <p>Paired with {@link justfatlard.pandorical.changelog.Changelog}, which is deliberately silent
 * on a first visit because there is no "since" for somebody who has never been. Between them, every
 * arrival gets the one that fits it.
 */
public final class Brief implements BriefApi {
	public static final Brief INSTANCE = new Brief();

	private Brief() {}

	/** The kind a notice is filed under. */
	public static final String KIND = "pandorical:brief";

	/** Remembers that this player has been offered it, so it is offered once and not every join. */
	private static final String BRIEFED = "pandorical:briefed";

	private final Map<String, List<String>> overviews = new ConcurrentHashMap<>();

	@Override
	public BriefApi overview(String modId, String... lines) {
		if (modId == null || lines == null || lines.length == 0) {
			Pandorical.LOGGER.warn(
				"[pandorical] a brief overview needs a mod id and at least one line - ignored ({})", modId);
			return this;
		}
		overviews.put(modId, List.of(lines));
		return this;
	}

	/**
	 * What one mod says it is: its own words, or the summary in its own metadata.
	 *
	 * <p>The same fallback {@link #entries} uses, for one mod rather than all of them. Wanted by
	 * the changelog, where a mod arriving for the first time needs saying what it is rather than
	 * what changed in it.
	 */
	public List<String> overviewOf(String modId) {
		justfatlard.pandorical.changelog.Changelog.loadNoteFiles();
		List<String> lines = overviews.get(modId);
		if (lines != null) return lines;
		ModCatalog.ModInfo mod = ModCatalog.find(modId);
		String summary = mod == null || mod.description() == null ? "" : mod.description().strip();
		return summary.isEmpty() ? List.of() : List.of(summary);
	}

	/** Whether this mod's overview came from code, which a file beside it must not overrule. */
	public boolean declared(String modId) {
		return overviews.containsKey(modId);
	}

	/** One mod, as the brief says it. */
	public record Entry(String modId, String name, List<String> lines) {}

	/**
	 * Every mod worth a line, in the order the mods screen lists them.
	 *
	 * <p>A mod that declared nothing falls back to the summary in its own metadata, and one with
	 * neither is left out rather than listed as a name with nothing beside it.
	 */
	public List<Entry> entries() {
		justfatlard.pandorical.changelog.Changelog.loadNoteFiles();
		List<Entry> out = new ArrayList<>();
		for (ModCatalog.ModInfo mod : ModCatalog.all()) {
			List<String> lines = overviews.get(mod.id());
			if (lines == null) {
				String summary = mod.description() == null ? "" : mod.description().strip();
				if (summary.isEmpty()) continue;
				lines = List.of(summary);
			}
			out.add(new Entry(mod.id(), mod.name(), lines));
		}
		return out;
	}

	/** Whether this player has already been offered it. */
	private static boolean briefed(ServerPlayer player, MinecraftServer server) {
		return PlayerSettings.get(server).get(player.getUUID(), BRIEFED) != null;
	}

	/**
	 * Put it to the player, once ever.
	 *
	 * <p>Marked as offered at the same moment, not when they read it: somebody who turns it down is
	 * not asked again every time they log in, which is how a welcome becomes a nuisance.
	 *
	 * <p>Worded for anybody rather than for a newcomer, because the two cannot be told apart here.
	 * On the first server to run this, every player is unbriefed, including the ones who have been
	 * coming for years, and the changelog's record of them is written on the same join a moment
	 * earlier - so leaning on it would suppress the brief for exactly the new players it is for.
	 * An overview of what is installed is worth one read either way.
	 */
	public static void offerTo(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null) return;
		if (briefed(player, server)) return;
		List<Entry> entries = INSTANCE.entries();
		if (entries.isEmpty()) return;

		PlayerSettings.get(server).put(player.getUUID(), BRIEFED, "1");
		PandoricalApi.notices().offer(player, new NoticeApi.Notice(
			"brief", KIND, "minecraft:map",
			"A quick look at the " + entries.size() + " mods running here",
			List.of(new NoticeApi.Choice("read", "minecraft:spyglass", "Have a look"),
				new NoticeApi.Choice("dismiss", "minecraft:barrier", "Not now")),
			0));
	}

	/**
	 * Show it on purpose, whenever somebody asks.
	 *
	 * <p>The offer is marked as made when it is put to the player rather than when they read it,
	 * so that a "not now" is not asked again every login. Without a way back, that same choice
	 * also meant never - and the one screen that says what a server is would be the one thing on
	 * it a player could permanently lose by being busy the minute they arrived.
	 */
	public static void show(ServerPlayer player) {
		BriefScreen.open(player);
	}

	/** Wired once, from Pandorical's own init. */
	public static void register() {
		PandoricalApi.notices().onChoice(KIND, (player, noticeId, choiceId) -> {
			if ("read".equals(choiceId)) {
				BriefScreen.open(player);
			} else {
				// Turned down, and this is the only moment they will be offered it, so the way
				// back goes with the refusal. Said because they just pressed a button, not
				// announced at somebody who was trying to play.
				player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
					"It is at /pandorical brief whenever you want it."));
			}
		});
		BriefScreen.register();
	}
}
