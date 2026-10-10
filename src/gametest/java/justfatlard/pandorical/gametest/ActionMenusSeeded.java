package justfatlard.pandorical.gametest;

import justfatlard.pandorical.actions.ActionMenuRegistry;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.client.actions.ActionMenus;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.util.List;

/**
 * The menus a server offers arrive, are the server's rather than the player's, and do not multiply.
 *
 * <p>The part of action menus that a player meets first and that no other test touches: joining and
 * finding something already there. Everything else about them is driven by hand in
 * {@code ActionMenusWork}; this is the half that only happens because a server said so.
 */
public final class ActionMenusSeeded implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> {
			ActionMenus.mine().clear();
			ActionMenus.save();
		});

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			world.getServer().waitFor(s -> PandoricalApi.isAvailable(connection.getServerPlayer()));
			context.waitTicks(10);

			List<String> names = menuNames(context);
			check(names.contains("Game"), "the game's own menu did not arrive: " + names);
			check(names.contains("Server"), "the server's menu did not arrive: " + names);
			check(names.contains("Probe menu"), "a mod's own menu did not arrive: " + names);

			// A first-page button is on Menus itself, ahead of the menus, and is not a menu of its own.
			List<String> first = context.computeOnClient(client -> ActionMenus.all().stream()
				.filter(menu -> menu.id.equals("pandorical:menus")).findFirst()
				.map(menu -> menu.buttons.stream().map(entry -> entry.label).toList()).orElse(List.of()));
			check(!first.isEmpty() && first.getFirst().equals("Probe first"), "the first-page button is not first on Menus: " + first);
			check(context.computeOnClient(client -> ActionMenus.all().stream()
					.noneMatch(menu -> menu.id.equals(justfatlard.pandorical.protocol.ActionMenusS2C.TOP_MENU_ID))),
				"the first-page buttons arrived as a menu of their own");

			// The server's menu opens with Pandorical's own: the brief, what's new and the mods screen.
			List<String> serverMenu = context.computeOnClient(client -> ActionMenus.menuById("pandorical:server").buttons.stream()
				.limit(3).map(entry -> entry.command).toList());
			check(serverMenu.equals(List.of("pandorical brief", "pandorical changes", "pandorical mods")),
				"the server menu does not open with Welcome, What's new and Mods: " + serverMenu);

			check(context.computeOnClient(client -> ActionMenus.menuById("pandorical:menus").buttons.stream()
					.anyMatch(entry -> "pandorical:server".equals(entry.menu) && entry.icon.equals("minecraft:beacon"))),
				"Server does not wear its beacon on Menus");
			check(context.computeOnClient(client -> ActionMenus.menuById("pandorical:menus").buttons.stream()
					.anyMatch(entry -> "pandorical:game".equals(entry.menu) && entry.icon.equals("minecraft:grass_block"))),
				"Game does not wear its grass block on Menus");

			// A button for somebody asks who before it runs: alone here, it says there is nobody.
			String asked = context.computeOnClient(client -> {
				ActionMenus.Entry who = new ActionMenus.Entry();
				who.command = "actionmenuprobe {player}";
				ActionMenus.run(who);
				String shown = client.gui.screen() == null ? "nothing" : client.gui.screen().getClass().getSimpleName();
				client.gui.setScreen(null);
				return shown;
			});
			check(asked.equals("PlayerPickerScreen"), "a button for {player} opened " + asked + " instead of asking who");

			// Perspective picks a view rather than stepping through them: a page of three, one press each,
			// reached from Game and not listed on Menus.
			check(context.computeOnClient(client -> {
				ActionMenus.Menu game = ActionMenus.menuById("pandorical:game");
				ActionMenus.Menu views = ActionMenus.menuById("pandorical:views");
				return game != null && views != null && views.buttons.size() == 3
					&& game.buttons.stream().anyMatch(entry -> ActionMenus.MENU.equals(entry.type) && "pandorical:views".equals(entry.menu))
					&& views.buttons.stream().allMatch(entry -> ActionMenus.CAMERA.equals(entry.type));
			}), "Perspective is not a page of three views");
			check(context.computeOnClient(client -> ActionMenus.menuById("pandorical:menus").buttons.stream()
					.noneMatch(entry -> "pandorical:views".equals(entry.menu))),
				"the views page is listed on Menus as a menu of its own");
			String behind = context.computeOnClient(client -> {
				ActionMenus.run(ActionMenus.menuById("pandorical:views").buttons.get(1));
				return client.options.getCameraType().name();
			});
			check(behind.equals("THIRD_PERSON_BACK"), "Behind left the camera " + behind);
			context.runOnClient(client -> ActionMenus.run(ActionMenus.menuById("pandorical:views").buttons.get(0)));

			// They are the server's: a player cannot edit them, and they are not saved as theirs.
			check(context.computeOnClient(client -> ActionMenus.mine().isEmpty()),
				"the server's menus were written into the player's own");
			check(context.computeOnClient(client ->
					ActionMenus.all().stream().allMatch(menu -> menu.builtin)),
				"a menu arrived that was not marked as the server's");

			// Every promoted button is offered for the player's own menus, whole.
			int promoted = context.computeOnClient(client -> ActionMenus.promoted().size());
			check(promoted >= 3, "only " + promoted + " promoted buttons were kept");
			check(context.computeOnClient(client -> ActionMenus.promoted().stream()
					.anyMatch(one -> one.button().command.equals("actionmenuprobe"))),
				"the probe button was not among the promoted");

			// A sprite for an icon arrives as the mod named it, for the button to draw.
			check(context.computeOnClient(client -> ActionMenus.promoted().stream()
					.anyMatch(one -> one.button().icon.equals("sprite:minecraft:icon/checkmark"))),
				"the sprite icon did not arrive as it was sent");

			// A button built from a promoted command wears what the mod chose for it.
			String made = context.computeOnClient(client -> {
				ActionMenus.Entry entry = ActionMenus.buttonFor("/actionmenuprobe");
				return entry.icon + " " + entry.label;
			});
			check(java.util.Set.of("minecraft:paper Probe", "minecraft:stone Probe again", "minecraft:diamond Probe first",
					"sprite:minecraft:icon/checkmark Probe sprite").contains(made),
				"a promoted command made a button of " + made);

			// Rebuilt on every join, so offering them again has to replace the set rather than
			// add to it. Offering them again is the whole point: a no-op here would leave this
			// assertion unable to fail, and the rebuild is what the README promises.
			int before = menuNames(context).size();
			check(before > 0, "no menus arrived at all, so multiplying them proves nothing");
			world.getServer().runOnServer(server ->
				ActionMenuRegistry.INSTANCE.offerTo(connection.getServerPlayer()));
			context.waitTicks(5);
			List<String> after = menuNames(context);
			check(after.size() == before,
				"offered twice, the menus went from " + before + " to " + after.size() + ": " + after);
		}

		context.waitTicks(10);
	}

	private static List<String> menuNames(ClientGameTestContext context) {
		return context.computeOnClient(client ->
			ActionMenus.all().stream().map(menu -> menu.name).toList());
	}

	private static void check(boolean ok, String complaint) {
		if (!ok) throw new AssertionError(complaint);
	}
}
