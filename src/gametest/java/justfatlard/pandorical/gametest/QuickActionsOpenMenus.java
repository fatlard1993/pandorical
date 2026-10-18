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

			// Sharing its key with a mod's (poopsmith's poop and quick actions are both G), it opens
			// nothing: a screen opening releases every key, and the mod's press would go unread.
			// The notices key claims a slot bound to N, so quick actions is put on N to share it.
			String shared = context.computeOnClient(client -> {
				var quickActions = client.options.keyQuickActions;
				String was = quickActions.saveString();
				quickActions.setKey(com.mojang.blaze3d.platform.InputConstants.getKey("key.keyboard.n"));
				net.minecraft.client.KeyMapping.resetMapping();
				try {
					var dialogs = client.player.connection.registryAccess().lookupOrThrow(Registries.DIALOG);
					client.player.connection.showDialog(dialogs.get(ResourceKey.create(Registries.DIALOG,
						Identifier.fromNamespaceAndPath("pandorical", "quick_actions"))).orElseThrow(), null);
					String screen = client.gui.screen() == null ? "nothing" : client.gui.screen().getClass().getSimpleName();
					client.gui.setScreen(null);
					return screen;
				} finally {
					quickActions.setKey(com.mojang.blaze3d.platform.InputConstants.getKey(was));
					net.minecraft.client.KeyMapping.resetMapping();
				}
			});
			check(shared.equals("nothing"), "quick actions on a mod's key opened " + shared
				+ ", which releases the key before the mod's press is read");
		});
	}

	private static void check(boolean ok, String complaint) {
		if (!ok) throw new AssertionError(complaint);
	}
}
