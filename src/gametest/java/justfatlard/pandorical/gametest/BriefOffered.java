package justfatlard.pandorical.gametest;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.brief.Brief;
import justfatlard.pandorical.settings.PlayerSettings;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * The brief reaches a player, says what each mod is, and keeps asking until they have read it.
 *
 * <p>Both halves are worth pinning, and they pull against each other. It used to be marked off the
 * moment it was offered, so anybody busy in their first minute - or who logged out before opening
 * the tray - had spent the only offer they would ever get, silently. Now it is marked when the
 * screen opens. The regression in the other direction is just as quiet: mark it nowhere and a
 * regular is asked on every login forever, which is how they stop reading notices at all.
 */
public final class BriefOffered implements FabricClientGameTest {

	/** The "has read it" key, not the old "was offered it" one it replaced. */
	private static final String BRIEFED = "pandorical:brief_read";

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "pandorical", session -> {
			AtomicReference<String> complaint = new AtomicReference<>();

			// The join already ran it, so a notice is waiting - and the player is not marked yet,
			// because they have not opened anything.
			session.onServer(server -> {
				if (PandoricalApi.notices().waiting(session.player()) < 1) {
					complaint.set("no notice reached the player on their first visit");
					return;
				}
				if (PlayerSettings.get(server).get(session.player().getUUID(), BRIEFED) != null) {
					complaint.set("being offered the brief counted as reading it");
				}
			});
			check(complaint.get() == null, complaint.get());

			// Every mod says something: its own words, or the summary from its metadata.
			session.onServer(server -> {
				List<Brief.Entry> entries = Brief.INSTANCE.entries();
				if (entries.isEmpty()) {
					complaint.set("the brief had nothing to say about any mod");
					return;
				}
				for (Brief.Entry entry : entries) {
					if (entry.lines().isEmpty() || entry.lines().stream().allMatch(String::isBlank)) {
						complaint.set(entry.modId() + " is listed with nothing beside it");
						return;
					}
				}
				// Pandorical's own is declared rather than fallen back to.
				Brief.Entry ours = entries.stream()
					.filter(entry -> entry.modId().equals("pandorical")).findFirst().orElse(null);
				if (ours == null || ours.lines().size() < 2) {
					complaint.set("Pandorical's own overview was not the one it declared: " + ours);
				}
			});
			check(complaint.get() == null, complaint.get());

			// Unread, it comes back.
			//
			// Put away first, and that is the whole point of doing it this way: offering the same
			// kind and id twice replaces the notice rather than stacking a second one, so a tray
			// that stays the same size proves nothing at all. Emptying it makes a repeat visible.
			session.onServer(server ->
				PandoricalApi.notices().withdrawAll(session.player(), Brief.KIND));
			session.waitTicks(2);
			int cleared = count(session);

			session.onServer(server -> Brief.offerTo(session.player()));
			session.waitTicks(5);
			int again = count(session);
			check(again > cleared,
				"the brief did not come back for a player who never read it: the tray stayed at "
					+ cleared + ", so being busy on arrival loses it for good");

			// Reading it is what ends it. The screen opens...
			session.onServer(server -> Brief.show(session.player()));
			session.waitTicks(5);
			String screen = context.computeOnClient(client -> client.gui.screen() == null ? "none"
				: client.gui.screen().getClass().getSimpleName());
			check(screen.endsWith("PandoricalScreen"),
				"the brief would not open: screen was " + screen);

			// ...and now it is marked off and stops asking.
			session.onServer(server -> {
				if (PlayerSettings.get(server).get(session.player().getUUID(), BRIEFED) == null) {
					complaint.set("reading the brief did not mark it as read");
				}
			});
			check(complaint.get() == null, complaint.get());

			session.onServer(server ->
				PandoricalApi.notices().withdrawAll(session.player(), Brief.KIND));
			session.waitTicks(2);
			int emptied = count(session);
			session.onServer(server -> Brief.offerTo(session.player()));
			session.waitTicks(5);
			int settled = count(session);
			check(settled == emptied,
				"the brief came back after being read: the tray went from " + emptied + " to "
					+ settled + ", so a regular would be asked on every login forever");
		});
	}

	private static int count(Smoke.Session session) {
		AtomicReference<Integer> waiting = new AtomicReference<>(0);
		session.onServer(server -> waiting.set(PandoricalApi.notices().waiting(session.player())));
		return waiting.get();
	}

	private static void check(boolean ok, String complaint) {
		if (!ok) throw new AssertionError(complaint);
	}
}
