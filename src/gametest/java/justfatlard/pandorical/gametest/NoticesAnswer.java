package justfatlard.pandorical.gametest;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import justfatlard.pandorical.api.NoticeApi;
import justfatlard.pandorical.api.PandoricalApi;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import justfatlard.pandorical.api.NavigableScreen;

/**
 * A question reaches a player, is answered with a button, and the mod that asked is told.
 *
 * <p>The whole point of the tray is that a mod can ask something without writing any UI, so what is
 * worth proving is the round trip: offered on the server, drawn on the client, pressed, and back to
 * the handler with the right answer. Every part of that is wiring, and wiring is what silently
 * stops working.
 */
public final class NoticesAnswer implements FabricClientGameTest {

	private static final String KIND = "pandorical-gametest:ask";

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "pandorical", session -> {
			// The tray is shared and its buttons are addressed by position, so anything the server
			// put there on join has to go before "the first button" means this test's own.
			session.onServer(server -> {
				PandoricalApi.notices().withdrawAll(session.player(),
					justfatlard.pandorical.brief.Brief.KIND);
				PandoricalApi.notices().withdrawAll(session.player(),
					justfatlard.pandorical.changelog.Changelog.KIND);
			});

			AtomicReference<String> heard = new AtomicReference<>();
			session.onServer(server -> PandoricalApi.notices().onChoice(KIND,
				(player, noticeId, choiceId) -> heard.set(noticeId + "/" + choiceId)));

			session.onServer(server -> PandoricalApi.notices().offer(session.player(),
				new NoticeApi.Notice("ask-1", KIND, "minecraft:ender_pearl",
					"Somebody wants to teleport to you",
					List.of(new NoticeApi.Choice("yes", "minecraft:lime_dye", "Accept"),
						new NoticeApi.Choice("no", "minecraft:barrier", "Decline")),
					0)));
			session.waitTicks(5);

			// Counted as a change rather than as a total. The tray is shared - joining now puts
			// the server's own brief in it too - so a test that insists on being alone in there
			// is a test that fails the day anything else has something to say.
			AtomicReference<Integer> landed = new AtomicReference<>(0);
			session.onServer(server -> landed.set(PandoricalApi.notices().waiting(session.player())));
			if (landed.get() < 1) {
				throw new AssertionError("the notice did not land");
			}

			// Offering the same id again is the same question, not a second one.
			session.onServer(server -> PandoricalApi.notices().offer(session.player(),
				new NoticeApi.Notice("ask-1", KIND, "minecraft:ender_pearl", "Asked twice",
					List.of(new NoticeApi.Choice("yes", "minecraft:lime_dye", "Accept")), 0)));
			session.onServer(server -> {
				if (PandoricalApi.notices().waiting(session.player()) != landed.get()) {
					throw new AssertionError("asking twice under one id stacked up");
				}
			});

			// The corner badge, photographed before the tray covers it. This is the half that was
			// wrong and that no assertion here would have caught: it was drawn by a call that
			// positions without sizing, so it clipped against the screen edge to a white sliver.
			// The notice had landed, the count was right, and every check below passed while what
			// the player actually saw was a stray mark in the corner.
			context.takeScreenshot("notices-badge");

			session.onServer(server -> PandoricalApi.notices().open(session.player()));
			session.waitTicks(5);
			if (!screenName(context).endsWith("PandoricalScreen")) {
				throw new AssertionError("the tray did not open: " + screenName(context));
			}
			context.takeScreenshot("notices-tray");

			clickComponent(context, "b0_0");
			session.waitTicks(5);

			if (!"ask-1/yes".equals(heard.get())) {
				throw new AssertionError("the answer came back as " + heard.get());
			}
			session.onServer(server -> {
				if (PandoricalApi.notices().waiting(session.player()) != 0) {
					throw new AssertionError("answering did not spend the notice");
				}
			});

			// And the way out. This is the one that was broken in play and green in here for
			// weeks: every other check above asks the server what it thinks, and the server
			// thought it had closed the screen. It had sent a close for an id the client had
			// never been given - a ScreenBuilder names itself by type and takes a random id, and
			// the close asked for by type matched neither - so the tray stayed open and the
			// button looked dead. Nothing throws when that happens; the screen just sits there.
			// So this asks the client what is actually on screen, which is the only witness that
			// would have noticed.
			session.onServer(server -> PandoricalApi.notices().offer(session.player(),
				new NoticeApi.Notice("ask-2", KIND, "minecraft:ender_pearl", "Still here?",
					List.of(new NoticeApi.Choice("yes", "minecraft:lime_dye", "Accept")), 0)));
			session.onServer(server -> PandoricalApi.notices().open(session.player()));
			session.waitTicks(5);
			if (!screenName(context).endsWith("PandoricalScreen")) {
				throw new AssertionError("the tray did not open for the close test: " + screenName(context));
			}

			clickComponent(context, "close");
			session.waitTicks(5);
			if (screenName(context).endsWith("PandoricalScreen")) {
				throw new AssertionError("the tray is still open after its own close button was pressed");
			}

			// A tray with something actually in it, for the picture. One notice with one choice
			// is the easiest case and the least like what a player sees, so it is the one layout
			// worth not judging the screen by: two questions, two answers each, a summary long
			// enough to wrap and a clock running on one of them.
			session.onServer(server -> {
				PandoricalApi.notices().offer(session.player(), new NoticeApi.Notice(
					"look-1", KIND, "minecraft:ender_pearl",
					"Fatlard asks to teleport to you, from somewhere in the nether",
					List.of(new NoticeApi.Choice("yes", "minecraft:lime_dye", "Bring them"),
						new NoticeApi.Choice("no", "minecraft:barrier", "Leave them")), 30));
				PandoricalApi.notices().offer(session.player(), new NoticeApi.Notice(
					"look-2", KIND, "minecraft:writable_book", "The village has post for you",
					List.of(new NoticeApi.Choice("read", "minecraft:paper", "Read it"),
						new NoticeApi.Choice("later", "minecraft:barrier", "Later")), 0));
				PandoricalApi.notices().open(session.player());
			});
			session.waitTicks(5);
			context.takeScreenshot("notices-tray-full");

			// And the badge as a returning player sees it. The one photographed at the top of
			// this test is the first-time badge, with the key on it; by now this player has
			// opened the tray several times, so the hint has done its job and the badge is a
			// bell and a number. Both are photographed because the difference between them is
			// the whole feature, and neither would fail a check if it silently became the other.
			session.onServer(server -> PandoricalApi.screens().close(session.player(), "pandorical:notices"));
			session.waitTicks(5);
			context.takeScreenshot("notices-badge-known");
		});
	}

	private static String screenName(ClientGameTestContext context) {
		return context.computeOnClient(client -> client.gui.screen() == null ? "none"
			: client.gui.screen().getClass().getSimpleName());
	}

	/**
	 * A click in the middle of a declared component, by the id it was declared under.
	 *
	 * <p>Not through {@code children()}: a screen the server declared draws a tree of its own
	 * components and registers no vanilla widgets at all, so that list is empty. The regions it
	 * offers a controller for navigation are the same boxes a mouse wants, and they carry the ids.
	 */
	private static void clickComponent(ClientGameTestContext context, String componentId) {
		double[] at = context.computeOnClient(client -> {
			if (!(client.gui.screen() instanceof NavigableScreen navigable)) return null;
			for (NavigableScreen.NavRegion region : navigable.navRegions()) {
				if (!region.id().equals(componentId)) continue;
				double scale = client.getWindow().getGuiScale();
				return new double[] {region.centerX() * scale, region.centerY() * scale};
			}
			return null;
		});
		if (at == null) throw new AssertionError("no component " + componentId + " on the screen");
		context.getInput().setCursorPos(at[0], at[1]);
		context.waitTick();
		context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
	}
}
