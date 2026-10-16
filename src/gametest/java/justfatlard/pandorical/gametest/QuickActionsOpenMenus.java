package justfatlard.pandorical.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * The quick-actions dialog the server ships is for players without Pandorical. On a Pandorical
 * client, choosing it opens the action menus it stands in for, and the dialog never shows.
 */
public final class QuickActionsOpenMenus implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "pandorical", session -> {
			String shown = context.computeOnClient(client -> {
				var dialogs = client.player.connection.registryAccess().lookupOrThrow(Registries.DIALOG);
				var ours = dialogs.get(ResourceKey.create(Registries.DIALOG, Identifier.fromNamespaceAndPath("pandorical", "quick_actions")));
				if (ours.isEmpty()) return "no pandorical:quick_actions dialog reached the client";
				client.player.connection.showDialog(ours.get(), null);
				String screen = client.gui.screen() == null ? "nothing" : client.gui.screen().getClass().getSimpleName();
				client.gui.setScreen(null);
				return screen;
			});
			check(shown.equals("ActionMenuScreen"), "the shipped quick action opened " + shown + ", not the action menus");
		});
	}

	private static void check(boolean ok, String complaint) {
		if (!ok) throw new AssertionError(complaint);
	}
}
