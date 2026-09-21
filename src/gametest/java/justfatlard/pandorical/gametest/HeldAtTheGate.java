package justfatlard.pandorical.gametest;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import justfatlard.pandorical.Arrivals;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.brief.Brief;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * A player being held short of play is left alone, and served the moment they are let in.
 *
 * <p>A mod can keep somebody in a pen at the spawn until they have said a password. Everything
 * Pandorical offers on the way in is addressed to somebody who has arrived, and put to somebody
 * still at the gate it is noise over the one instruction they need - and the list of every mod
 * installed here is not a thing to hand out before the password is answered.
 *
 * <p>The half that would rot quietly is the second one. Holding the offers back is easy to get
 * right and easy to leave held: a player let in and never served looks exactly like a player who
 * dismissed a notice, and nobody would trace it here.
 */
public final class HeldAtTheGate implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "pandorical", session -> {
			AtomicBoolean held = new AtomicBoolean(true);
			AtomicReference<String> complaint = new AtomicReference<>();

			session.onServer(server -> PandoricalApi.heldWhile(player -> held.get()));

			// This player already arrived properly when the world came up, so both offers are
			// spent and would decline a second time for the right reason. Give the changelog
			// something real to say, or "held back" and "nothing to say" look alike and the test
			// cannot tell which of them it proved.
			session.onServer(server -> {
				PandoricalApi.notices().withdrawAll(session.player(), Brief.KIND);
				PandoricalApi.notices().withdrawAll(session.player(),
					justfatlard.pandorical.changelog.Changelog.KIND);
				justfatlard.pandorical.settings.PlayerSettings.get(server).put(
					session.player().getUUID(), "pandorical:mods_seen", "pandorical=0.0.1\n");
			});
			session.waitTicks(2);

			session.onServer(server -> {
				int before = PandoricalApi.notices().waiting(session.player());
				Arrivals.arrived(session.player());
				if (PandoricalApi.notices().waiting(session.player()) != before) {
					complaint.set("a player still at the gate was handed something anyway");
				}
			});
			session.waitTicks(5);
			check(complaint.get() == null, complaint.get());

			// Let them in. Arrivals checks once a second, so give it more than that.
			held.set(false);
			session.waitTicks(40);

			session.onServer(server -> {
				if (PandoricalApi.notices().waiting(session.player()) < 1) {
					complaint.set("a player let in was never served: held back is not the same as"
						+ " dropped, and nobody would ever notice the difference");
				}
			});
			check(complaint.get() == null, complaint.get());
		});
	}

	private static void check(boolean ok, String complaint) {
		if (!ok) throw new AssertionError(complaint);
	}
}
