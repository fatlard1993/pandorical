package justfatlard.pandorical.gametest;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.changelog.Changelog;
import justfatlard.pandorical.settings.PlayerSettings;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * A player who was away is told what moved while they were gone, and a player who has never been
 * is told nothing at all.
 *
 * <p>Both halves matter and only one of them is obvious. The silence on a first visit is the part
 * that would rot quietly: nothing breaks if it starts announcing forty mods to somebody who has
 * never played here, it just makes a bad first minute that nobody would trace back to here.
 */
public final class WhatsNew implements FabricClientGameTest {

	private static final String SEEN = "pandorical:mods_seen";

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "pandorical", session -> {
			// The join already ran the real thing. A first visit has no "since".
			AtomicReference<String> firstVisit = new AtomicReference<>();
			session.onServer(server ->
				firstVisit.set(Changelog.INSTANCE.pendingFor(session.player()).isEmpty()
					? null : "a first visit was told what changed"));
			check(firstVisit.get() == null, firstVisit.get());

			// Now give them a past: an older Pandorical, and a mod that has since gone away.
			String version = Pandorical.modVersion();
			session.onServer(server -> {
				PandoricalApi.changelog().note("pandorical", version,
					"The mods screen can switch one of your own mods off.");
				// An older one too, so "newest first" has something to be wrong about.
				PandoricalApi.changelog().note("pandorical", "0.9.0", "Something from before.");
				PlayerSettings.get(server).put(session.player().getUUID(), SEEN,
					"pandorical=0.0.1\nlong-gone=1.0.0\n");
			});
			session.onServer(server -> Changelog.offerTo(session.player()));
			session.waitTicks(5);

			AtomicReference<String> complaint = new AtomicReference<>();
			session.onServer(server -> {
				List<Changelog.Change> changes = Changelog.INSTANCE.pendingFor(session.player());

				Changelog.Change updated = find(changes, "pandorical");
				if (updated == null) {
					complaint.set("the mod that moved from 0.0.1 to " + version + " was not reported");
					return;
				}
				if (updated.kind() != Changelog.Kind.UPDATED) {
					complaint.set("a version change was reported as " + updated.kind());
					return;
				}
				// The authored note is the point: a version pair is the fallback, not the feature.
				if (!updated.lines().contains("The mods screen can switch one of your own mods off.")) {
					complaint.set("the note the mod wrote was not carried: " + updated.lines());
					return;
				}

				Changelog.Change gone = find(changes, "long-gone");
				if (gone == null || gone.kind() != Changelog.Kind.REMOVED) {
					complaint.set("a mod that is no longer here was not reported as gone");
					return;
				}

				// And it was actually put to the player, not just worked out.
				if (PandoricalApi.notices().waiting(session.player()) < 1) {
					complaint.set("the changes were worked out but no notice reached the player");
				}
			});
			check(complaint.get() == null, complaint.get());

			// A mod the player has never seen is introduced, not patch-noted. Most mods arrive on
			// a patch version, where the note for that version is the least useful thing that
			// could be said to somebody meeting it, so this is the common case and not the edge.
			AtomicReference<String> introduced = new AtomicReference<>();
			session.onServer(server -> {
				List<Changelog.Change> changes = Changelog.INSTANCE.pendingFor(session.player());
				Changelog.Change arrived = null;
				for (Changelog.Change change : changes) {
					if (change.kind() == Changelog.Kind.ADDED) arrived = change;
				}
				if (arrived == null) {
					introduced.set("nothing was reported as newly arrived, so this proves nothing");
					return;
				}
				List<String> said = arrived.lines();
				List<String> overview = justfatlard.pandorical.brief.Brief.INSTANCE
					.overviewOf(arrived.modId());
				if (!overview.isEmpty() && !said.equals(overview)) {
					introduced.set("a newly added mod was described by " + said
						+ " rather than by what it is: " + overview);
				}
			});
			check(introduced.get() == null, introduced.get());

			// The same notes are readable on purpose, from the mod's own page, not only in the
			// moment they arrive. A player who dismissed the notice has not lost them.
			AtomicReference<String> onThePage = new AtomicReference<>();
			session.onServer(server -> {
				if (!Changelog.INSTANCE.hasNotes("pandorical")) {
					onThePage.set("the mods screen would offer no Changes tab for a mod with notes");
					return;
				}
				List<Changelog.Released> released = Changelog.INSTANCE.releases("pandorical");
				if (released.isEmpty()) {
					onThePage.set("a mod with notes listed no releases");
					return;
				}
				// Newest first: somebody opening the tab wants the latest at the top.
				if (released.size() < 2) {
					onThePage.set("both declared versions should be listed, got " + released.size());
					return;
				}
				// Both declared versions are here, newest at the top, and 0.9.0 below whatever
				// else the mod has shipped since.
				//
				// By position, this used to be: index 0 the current version and index 1 exactly
				// "0.9.0". That held only while this test's own two notes were the whole list, so
				// the first real release note added to Pandorical put a third entry between them
				// and failed a test about ordering with a list that was correctly ordered. What
				// the test means is "newest first", so that is what it now asks.
				List<String> versions = released.stream().map(Changelog.Released::version).toList();
				if (!versions.get(0).equals(version)) {
					onThePage.set("the newest release was not at the top: " + versions);
				} else if (versions.indexOf("0.9.0") < 0) {
					onThePage.set("the older declared version was not listed: " + versions);
				} else if (versions.indexOf("0.9.0") != versions.size() - 1) {
					onThePage.set("the oldest release was not at the bottom: " + versions);
				}
			});
			check(onThePage.get() == null, onThePage.get());

			// A mod that ships a file and no code is read all the same. pandorical-gametest
			// declares nothing in Java; everything below comes out of its resources.
			AtomicReference<String> fromFile = new AtomicReference<>();
			session.onServer(server -> {
				List<Changelog.Released> theirs = Changelog.INSTANCE.releases("pandorical-gametest");
				if (theirs.size() != 2) {
					fromFile.set("a mod's changelog file was not read: " + theirs);
					return;
				}
				if (!theirs.get(0).version().equals("1.1.0")) {
					fromFile.set("file notes were not newest first: " + theirs.get(0).version());
					return;
				}
				if (!theirs.get(0).lines().get(0).startsWith("Notes read out of a file")) {
					fromFile.set("the file's own words did not survive: " + theirs.get(0).lines());
				}
			});
			check(fromFile.get() == null, fromFile.get());

			// Asked again with nothing new, it stays quiet rather than repeating itself.
			session.onServer(server -> Changelog.offerTo(session.player()));
			session.waitTicks(5);
			AtomicReference<String> repeated = new AtomicReference<>();
			session.onServer(server ->
				repeated.set(Changelog.INSTANCE.pendingFor(session.player()).isEmpty()
					? null : "it reported the same changes a second time"));
			check(repeated.get() == null, repeated.get());
		});
	}

	private static Changelog.Change find(List<Changelog.Change> changes, String modId) {
		for (Changelog.Change change : changes) {
			if (change.modId().equals(modId)) return change;
		}
		return null;
	}

	private static void check(boolean ok, String complaint) {
		if (!ok) throw new AssertionError(complaint);
	}
}
