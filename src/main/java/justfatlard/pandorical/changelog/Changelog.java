package justfatlard.pandorical.changelog;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.api.ChangelogApi;
import justfatlard.pandorical.api.NoticeApi;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.settings.ModCatalog;
import justfatlard.pandorical.settings.PlayerSettings;
import net.fabricmc.loader.api.Version;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What changed on this server while a player was away.
 *
 * <p>A server moves on between visits and nothing ever says so: mods arrive, leave and change
 * under a player who has no way to notice until something they relied on behaves differently.
 * This is the difference between the mods they last saw and the ones here now, put to them once,
 * on the way in.
 *
 * <p>It is a notice rather than a line of chat because chat is where it would be missed. A notice
 * waits in the tray until it is answered, which is the whole reason the tray exists.
 *
 * <p>What each mod has to say about itself is the mod's own, through {@link ChangelogApi}. A mod
 * that says nothing is still reported, by the versions it moved between, because "this changed and
 * nobody said how" beats silence.
 */
public final class Changelog implements ChangelogApi {
	public static final Changelog INSTANCE = new Changelog();

	private Changelog() {}

	/** The kind a notice is filed under, and the screen's own action namespace. */
	public static final String KIND = "pandorical:whats_new";

	/** Where a player's last visit is kept, in the world save beside their other settings. */
	private static final String SEEN = "pandorical:mods_seen";

	/** Notes by mod id, then by the version they describe. */
	private final Map<String, Map<String, List<String>>> notes = new ConcurrentHashMap<>();

	/**
	 * What each player was told changed, until they look at it or leave.
	 *
	 * <p>The difference is worked out once, on the way in, and the visit is recorded in the same
	 * breath: asking again when they press the button would compare now against now and find
	 * nothing. So the answer is kept here for as long as the question is on screen.
	 */
	private final Map<java.util.UUID, List<Change>> pending = new ConcurrentHashMap<>();

	/** What this player was told changed, for the screen that shows it. */
	public List<Change> pendingFor(ServerPlayer player) {
		return pending.getOrDefault(player.getUUID(), List.of());
	}

	/** Leaving ends it; the next visit works its own difference out. */
	public static void forget(ServerPlayer player) {
		INSTANCE.pending.remove(player.getUUID());
	}

	@Override
	public ChangelogApi note(String modId, String version, String... lines) {
		if (modId == null || version == null || lines == null || lines.length == 0) {
			Pandorical.LOGGER.warn(
				"[pandorical] changelog note needs a mod id, a version and at least one line - ignored ({} {})",
				modId, version);
			return this;
		}
		notes.computeIfAbsent(modId, id -> new ConcurrentHashMap<>())
			.put(version, List.of(lines));
		return this;
	}

	/** One version of a mod, and what it said about itself. */
	public record Released(String version, List<String> lines) {}

	/**
	 * Every version this mod has notes for, newest first.
	 *
	 * <p>For the mods screen, which shows a mod's whole history rather than the slice one player
	 * missed: somebody reading a mod's page is asking what it has been doing, not what changed
	 * since Tuesday.
	 */
	public List<Released> releases(String modId) {
		NoteFiles.loadOnce();
		Map<String, List<String>> mine = notes.get(modId);
		if (mine == null) return List.of();
		List<String> versions = new ArrayList<>(ordered(mine.keySet()));
		java.util.Collections.reverse(versions);
		List<Released> out = new ArrayList<>();
		for (String version : versions) out.add(new Released(version, mine.get(version)));
		return out;
	}

	/** Whether this version's note came from code, which a file beside it must not overrule. */
	boolean declared(String modId, String version) {
		Map<String, List<String>> mine = notes.get(modId);
		return mine != null && mine.containsKey(version);
	}

	/** Whether this mod has written anything down, for a screen deciding whether to offer a tab. */
	public boolean hasNotes(String modId) {
		NoteFiles.loadOnce();
		Map<String, List<String>> mine = notes.get(modId);
		return mine != null && !mine.isEmpty();
	}

	/** What a mod said about one version, or nothing. */
	List<String> notesFor(String modId, String version) {
		Map<String, List<String>> mine = notes.get(modId);
		List<String> found = mine == null ? null : mine.get(version);
		return found == null ? List.of() : found;
	}

	/** One mod's worth of news. */
	public record Change(Kind kind, String modId, String name, String from, String to, List<String> lines) {}

	public enum Kind { ADDED, UPDATED, REMOVED }

	/**
	 * The difference between what this player last saw and what is here now.
	 *
	 * <p>Empty on a player's first visit, deliberately: there is no "since" for them, and forty
	 * mods introducing themselves at once is not a welcome.
	 */
	public List<Change> since(ServerPlayer player) {
		NoteFiles.loadOnce();
		net.minecraft.server.MinecraftServer server = player.level().getServer();
		if (server == null) return List.of();
		PlayerSettings settings = PlayerSettings.get(server);
		String packed = settings.get(player.getUUID(), SEEN);
		Map<String, String> now = current();
		if (packed == null) {
			// First visit. Remember where they came in, say nothing.
			settings.put(player.getUUID(), SEEN, pack(now));
			return List.of();
		}
		Map<String, String> seen = unpack(packed);
		List<Change> changes = new ArrayList<>();

		for (Map.Entry<String, String> entry : now.entrySet()) {
			String was = seen.get(entry.getKey());
			if (was == null) {
				// A mod this player has never seen is introduced, not patch-noted. Telling
				// somebody meeting Emerald Armor that the villagers have remarks about walking
				// around in emeralds answers a question they have not got to yet; what it is
				// comes first, and most mods arrive on a patch version where the note for that
				// version is the least useful sentence about them.
				List<String> saying = justfatlard.pandorical.brief.Brief.INSTANCE
					.overviewOf(entry.getKey());
				if (saying.isEmpty()) saying = notesFor(entry.getKey(), entry.getValue());
				changes.add(new Change(Kind.ADDED, entry.getKey(), nameOf(entry.getKey()),
					null, entry.getValue(), saying));
			} else if (!was.equals(entry.getValue())) {
				changes.add(new Change(Kind.UPDATED, entry.getKey(), nameOf(entry.getKey()),
					was, entry.getValue(), between(entry.getKey(), was, entry.getValue())));
			}
		}
		for (Map.Entry<String, String> entry : seen.entrySet()) {
			if (!now.containsKey(entry.getKey())) {
				changes.add(new Change(Kind.REMOVED, entry.getKey(), entry.getKey(),
					entry.getValue(), null, List.of()));
			}
		}
		settings.put(player.getUUID(), SEEN, pack(now));
		return changes;
	}

	/**
	 * Every note for a version the player has not seen: newer than the one they left on, no newer
	 * than the one here now. A player away for three releases reads all three.
	 *
	 * <p>Where a version will not parse as one, only the note for the version actually running is
	 * shown. Guessing an order from strings is how somebody gets told about a release that came
	 * out before they last played.
	 */
	private List<String> between(String modId, String from, String to) {
		Map<String, List<String>> mine = notes.get(modId);
		if (mine == null) return List.of();
		List<String> out = new ArrayList<>();
		for (String version : ordered(mine.keySet())) {
			if (covers(from, version, to)) out.addAll(mine.get(version));
		}
		return out;
	}

	private static boolean covers(String from, String version, String to) {
		try {
			Version left = Version.parse(from);
			Version at = Version.parse(version);
			Version right = Version.parse(to);
			return left.compareTo(at) < 0 && at.compareTo(right) <= 0;
		} catch (Exception e) {
			return version.equals(to);
		}
	}

	/**
	 * Oldest first for the versions that parse, then the ones that do not, in the order declared.
	 *
	 * <p>Sorted on parsed values rather than by a comparator that parses as it goes: one that
	 * returns nothing useful for an unparseable pair contradicts itself, and the sort is entitled
	 * to throw when it notices.
	 */
	private static List<String> ordered(java.util.Collection<String> versions) {
		List<String> parsed = new ArrayList<>();
		List<String> rest = new ArrayList<>();
		Map<String, Version> known = new LinkedHashMap<>();
		for (String version : versions) {
			try {
				known.put(version, Version.parse(version));
				parsed.add(version);
			} catch (Exception e) {
				rest.add(version);
			}
		}
		parsed.sort((a, b) -> known.get(a).compareTo(known.get(b)));
		parsed.addAll(rest);
		return parsed;
	}

	private static String nameOf(String modId) {
		ModCatalog.ModInfo info = ModCatalog.find(modId);
		return info == null ? modId : info.name();
	}

	/** Every mod this server is running, by id, as it would name itself. */
	private static Map<String, String> current() {
		Map<String, String> out = new LinkedHashMap<>();
		for (ModCatalog.ModInfo mod : ModCatalog.all()) out.put(mod.id(), mod.version());
		return out;
	}

	// One id=version per line. Mod ids carry no newline and versions carry no newline, so there is
	// nothing here to escape and nothing that needs a parser.
	private static String pack(Map<String, String> mods) {
		StringBuilder out = new StringBuilder();
		for (Map.Entry<String, String> entry : mods.entrySet()) {
			out.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
		}
		return out.toString();
	}

	private static Map<String, String> unpack(String packed) {
		Map<String, String> out = new LinkedHashMap<>();
		for (String line : packed.split("\n")) {
			int split = line.indexOf('=');
			if (split > 0) out.put(line.substring(0, split), line.substring(split + 1));
		}
		return out;
	}

	/** Put it to the player, if there is anything to put. */
	public static void offerTo(ServerPlayer player) {
		List<Change> changes = INSTANCE.since(player);
		if (changes.isEmpty()) {
			// Nothing new, so nothing is being offered. Without this the last visit's list stays
			// behind it, and whatever reads it next is reading a question already answered.
			INSTANCE.pending.remove(player.getUUID());
			return;
		}
		INSTANCE.pending.put(player.getUUID(), changes);

		int added = 0;
		int updated = 0;
		int removed = 0;
		for (Change change : changes) {
			switch (change.kind()) {
				case ADDED -> added++;
				case UPDATED -> updated++;
				case REMOVED -> removed++;
			}
		}
		PandoricalApi.notices().offer(player, new NoticeApi.Notice(
			"whats-new", KIND, "minecraft:written_book", summary(added, updated, removed),
			List.of(new NoticeApi.Choice("show", "minecraft:spyglass", "Show me"),
				new NoticeApi.Choice("dismiss", "minecraft:barrier", "Not now")),
			0));
	}

	static String summary(int added, int updated, int removed) {
		List<String> parts = new ArrayList<>();
		if (added > 0) parts.add(added + (added == 1 ? " new mod" : " new mods"));
		if (updated > 0) parts.add(updated + " updated");
		if (removed > 0) parts.add(removed + " gone");
		return String.join(", ", parts) + " since you were last here";
	}

	/**
	 * Read what the mods shipped in files, once.
	 *
	 * <p>Public because the brief is built from the same files and lives in its own package. Called
	 * on the way into anything that reads notes rather than at init, because a mod initialising
	 * after Pandorical would otherwise have its file read before its own code had run.
	 */
	public static void loadNoteFiles() {
		NoteFiles.loadOnce();
	}

	/** Wired once, from Pandorical's own init. */
	public static void register() {
		PandoricalApi.notices().onChoice(KIND, (player, noticeId, choiceId) -> {
			if ("show".equals(choiceId)) {
				WhatsNewScreen.open(player);
			} else {
				// Turned down. The screen is only reachable from the notice, so the list it would
				// have shown has nothing left to show it to.
				INSTANCE.pending.remove(player.getUUID());
			}
		});
		WhatsNewScreen.register();
	}
}
