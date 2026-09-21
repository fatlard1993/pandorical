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
