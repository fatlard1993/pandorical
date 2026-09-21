package justfatlard.pandorical.gametest;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.brief.Brief;
import justfatlard.pandorical.settings.PlayerSettings;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * The brief reaches a player once, says what each mod is, and does not ask twice.
 *
 * <p>The once is the part worth pinning. Offering it again costs nothing visible the first time it
 * regresses - a notice in a tray - and turns a welcome into something a regular dismisses on every
 * login until they stop reading notices at all.
 */
public final class BriefOffered implements FabricClientGameTest {

	private static final String BRIEFED = "pandorical:briefed";

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "pandorical", session -> {
			AtomicReference<String> complaint = new AtomicReference<>();

			// The join already ran it, so this player has been briefed and told about it.
			session.onServer(server -> {
				if (PlayerSettings.get(server).get(session.player().getUUID(), BRIEFED) == null) {
					complaint.set("joining did not mark the player as briefed");
					return;
				}
				if (PandoricalApi.notices().waiting(session.player()) < 1) {
					complaint.set("no notice reached the player on their first visit");
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

			// Asked again, it holds its tongue.
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
			int after = count(session);
			check(after == cleared,
				"the brief came back after being put away: the tray went from " + cleared
					+ " to " + after + ", so a briefed player would be asked again every join");

			// ...but it is still theirs to open. Offered once and openable forever are different
			// promises, and the flag that keeps the first one would quietly break the second.
			session.onServer(server -> Brief.show(session.player()));
			session.waitTicks(5);
			String screen = context.computeOnClient(client -> client.gui.screen() == null ? "none"
				: client.gui.screen().getClass().getSimpleName());
			check(screen.endsWith("PandoricalScreen"),
				"a player who has been briefed could not open it again: screen was " + screen);
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
