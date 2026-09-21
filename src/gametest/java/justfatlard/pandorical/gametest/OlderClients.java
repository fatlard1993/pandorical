package justfatlard.pandorical.gametest;

import justfatlard.pandorical.api.ComponentBuilder;
import justfatlard.pandorical.api.ComponentType;
import justfatlard.pandorical.api.NotUnderstood;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.ScreenBuilder;
import justfatlard.pandorical.client.camera.CameraManager;
import justfatlard.pandorical.client.component.ComponentRegistry;
import justfatlard.pandorical.client.component.PanelComponent;
import justfatlard.pandorical.client.component.UnknownComponent;
import justfatlard.pandorical.client.screen.PandoricalScreen;
import justfatlard.pandorical.protocol.ActionMenusS2C;
import justfatlard.pandorical.protocol.CameraHintS2C;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * What this client does with a server newer than itself. Every player runs a client older than
 * some server eventually, so this is the behaviour the whole bridge rests on: the unknown is
 * skipped, never guessed at, and never fatal.
 *
 * <p>A word this client has no code for cannot be sent from a test, so the test says it the only
 * way a future server could: a type, a hint and a value this build has never heard of.
 */
public final class OlderClients implements FabricClientGameTest {
	private static final String FUTURE_TYPE = "pandorical-gametest:not_invented_yet";

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> {
			// An unknown type draws nothing at all. It used to draw an opaque grey panel, which put
			// a slab over whatever the server was showing.
			assertThat(ComponentRegistry.create(FUTURE_TYPE, null) instanceof UnknownComponent,
				"an unknown component type should draw nothing");
			assertThat(ComponentRegistry.create(FUTURE_TYPE, ComponentType.PANEL) instanceof PanelComponent,
				"an unknown component type should draw the server's fallback when it names one");
			assertThat(ComponentRegistry.create(FUTURE_TYPE, "also_not_a_type") instanceof UnknownComponent,
				"a fallback this client does not know should draw nothing either");
			assertThat(ComponentRegistry.create(ComponentType.PANEL, null) instanceof PanelComponent,
				"a known type should still be itself");

			// An unknown camera hint or perspective leaves the player's view alone.
			CameraManager.handleHint(new CameraHintS2C("from_the_future", Map.of()));
			CameraManager.handleHint(new CameraHintS2C("perspective", Map.of("mode", "sideways")));
			assertThat(client.options.getCameraType() == net.minecraft.client.CameraType.FIRST_PERSON,
				"an unknown perspective should leave the view as it was");
			CameraManager.reset();
		});

		// Values too long for the wire are trimmed rather than thrown on: an encode failure
		// disconnects the player it was meant for.
		ActionMenusS2C.Button huge = new ActionMenusS2C.Button("i".repeat(500), "l".repeat(500),
			"c".repeat(900), "k".repeat(500));
		assertThat(huge.label().length() == 128 && huge.command().length() == 512,
			"an overlong action button should be trimmed to what the wire takes");
		assertThat(new ActionMenusS2C(List.of()).menus().isEmpty(), "an empty menu list stays empty");

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			server.waitFor(s -> PandoricalApi.isContentReady(connection.getServerPlayer()));

			// The server author cannot read the player's log, so the client says it out loud.
			List<String> reported = new CopyOnWriteArrayList<>();
			server.runOnServer(s -> PandoricalApi.onNotUnderstood(
				(player, kind, value) -> reported.add(kind + " " + value)));

			// The whole point, end to end: a screen carrying a component this client has never
			// heard of still opens, and the rest of it still draws.
			String screenId = server.computeOnServer(s -> {
				ScreenBuilder screen = new ScreenBuilder("pandorical-gametest:older").size(120, 64).title("Older");
				screen.panel("bg", 0, 0, 120, 64, Map.of("border", "beveled"));
				screen.component(new ComponentBuilder("future", FUTURE_TYPE).bounds(8, 8, 40, 40)
					.prop(ComponentType.PROP_FALLBACK, ComponentType.PANEL));
				screen.component(new ComponentBuilder("future2", FUTURE_TYPE).bounds(60, 8, 40, 40));
				screen.text("line", 8, 52, "Still here.");
				PandoricalApi.screens().open(connection.getServerPlayer(), screen.build());
				return screen.screenId();
			});
			context.waitForScreen(PandoricalScreen.class);
			server.waitFor(s -> reported.contains(NotUnderstood.COMPONENT_TYPE + " " + FUTURE_TYPE));
			server.runOnServer(s -> PandoricalApi.screens().close(connection.getServerPlayer(), screenId));
			context.waitForScreen(null);
		}
	}

	private static void assertThat(boolean holds, String otherwise) {
		if (!holds) throw new AssertionError(otherwise);
	}
}
