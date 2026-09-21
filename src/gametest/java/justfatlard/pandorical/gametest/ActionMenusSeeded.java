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

			// A button built from a promoted command wears what the mod chose for it.
			String made = context.computeOnClient(client -> {
				ActionMenus.Entry entry = ActionMenus.buttonFor("/actionmenuprobe");
				return entry.icon + " " + entry.label;
			});
			check(made.equals("minecraft:paper Probe") || made.equals("minecraft:stone Probe again"),
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
