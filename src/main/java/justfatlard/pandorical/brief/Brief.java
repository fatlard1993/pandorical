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
 * offered until it has been read, and it ends by pointing at the mods screen for the rest.
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

	/**
	 * Remembers that this player has read it, so it is offered until seen and not forever.
	 *
	 * <p>A different key from the {@code pandorical:briefed} this used to write, because it means
	 * a different thing: that one was set when the brief was offered, this one when it is read.
	 * Reusing it would have left everybody already carrying it skipped forever - which is exactly
	 * the players this change is for, since under the old rule being offered it once and never
	 * opening it was indistinguishable from having read it. The old key is simply left alone;
	 * anybody who genuinely read the brief is asked once more, which is one click.
	 */
	private static final String BRIEFED = "pandorical:brief_read";

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

	/** Whether this player has already read it. */
	private static boolean briefed(ServerPlayer player, MinecraftServer server) {
		return PlayerSettings.get(server).get(player.getUUID(), BRIEFED) != null;
	}

	/**
	 * Put it to the player, on every join until they have actually read it.
	 *
	 * <p>It used to be marked the moment it was offered, so a player who was busy the minute they
	 * arrived, or who logged out before opening the tray, had used up the only offer they would
	 * ever get. That is the wrong way round for an orientation: a notice nobody opened has not
	 * done anything, and the cost of asking again is one line in a tray.
	 *
	 * <p>So it is marked when the screen is opened, and "not now" means not now. Reading it once
	 * ends it for good, which is a single click for anybody who does not want to be asked again.
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

		PandoricalApi.notices().offer(player, new NoticeApi.Notice(
			"brief", KIND, "minecraft:map",
			"A quick look at the " + entries.size() + " mods running here",
			List.of(new NoticeApi.Choice("read", "minecraft:spyglass", "Have a look"),
				new NoticeApi.Choice("dismiss", "minecraft:barrier", "Not now")),
			0));
	}

	/**
	 * Show it, and count it as read.
	 *
	 * <p>The one place the brief is marked off, so opening it from the notice and opening it from
	 * {@code /pandorical brief} settle the same thing. Anything that opens the screen without
	 * coming through here would leave a player being asked forever.
	 */
	public static void show(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server != null) PlayerSettings.get(server).put(player.getUUID(), BRIEFED, "1");
		BriefScreen.open(player);
	}

	/** Wired once, from Pandorical's own init. */
	public static void register() {
		PandoricalApi.notices().onChoice(KIND, (player, noticeId, choiceId) -> {
			if ("read".equals(choiceId)) {
				show(player);
			} else {
				// Turned down, which is not the same as done with: they will be asked again next
				// time they join. Said because they just pressed a button, not announced at
				// somebody who was trying to play.
				player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
					"It is at /pandorical brief whenever you want it."));
			}
		});
		BriefScreen.register();
	}
}
