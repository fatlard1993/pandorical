package justfatlard.pandorical.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.client.actions.ActionMenus;
import justfatlard.pandorical.protocol.ClientSettingS2C;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/**
 * An action menu opens on its key, a command button runs its command on the server, a key button
 * presses its key, and the entry on Pandorical's page of the mods menu opens the editor.
 */
public final class ActionMenusWork implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			server.waitFor(s -> PandoricalApi.isAvailable(connection.getServerPlayer()));

			context.runOnClient(client -> {
				ActionMenus.mine().clear();
				ActionMenus.Menu menu = new ActionMenus.Menu();
				menu.name = "Test menu";
				menu.key = "key.keyboard.g";
				ActionMenus.Entry probe = new ActionMenus.Entry();
				probe.label = "Probe";
				probe.command = "/actionmenuprobe";
				ActionMenus.Entry inventory = new ActionMenus.Entry();
				inventory.icon = "minecraft:chest";
				inventory.label = "Inventory";
				inventory.type = ActionMenus.KEY;
				inventory.keyMapping = "key.inventory";
				ActionMenus.Menu page = new ActionMenus.Menu();
				page.name = "Page two";
				page.buttons.add(probe.copyForTest());
				ActionMenus.Entry opens = new ActionMenus.Entry();
				opens.icon = "minecraft:book";
				opens.label = "More";
				opens.type = ActionMenus.MENU;
				opens.menu = page.id;
				menu.buttons.add(probe);
				menu.buttons.add(inventory);
				menu.buttons.add(opens);
				ActionMenus.mine().add(menu);
				ActionMenus.mine().add(page);
			});

			// Its key opens it.
			context.getInput().pressKey(InputConstants.KEY_G);
			context.waitTicks(3);
			check(screenName(context).endsWith("ActionMenuScreen"), "G opened " + screenName(context));
			context.takeScreenshot("action-menu");

			// The command button runs the command, as the player.
			int before = ActionMenuProbe.RUNS.get();
			clickWidget(context, 0);
			context.waitTicks(5);
			check(ActionMenuProbe.RUNS.get() == before + 1, "the command button did not run its command");
			check(screenName(context).equals("none"), "the menu stayed open: " + screenName(context));

			// The key button presses the key: the inventory opens.
			context.getInput().pressKey(InputConstants.KEY_G);
			context.waitTicks(3);
			clickWidget(context, 1);
			context.waitTicks(5);
			check(context.computeOnClient(client -> client.gui.screen() instanceof InventoryScreen),
				"the inventory key button opened " + screenName(context));
			context.runOnClient(client -> client.gui.setScreen(null));
			context.waitTicks(2);

			// A button that opens another menu opens it over the first; Escape goes back to the
			// first, and the key puts them all away. A command from the page runs as from the first.
			context.getInput().pressKey(InputConstants.KEY_G);
			context.waitTicks(3);
			clickWidget(context, 2);
			context.waitTicks(3);
			check(title(context).equals("Page two"), "the More button opened " + title(context));
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(3);
			check(title(context).equals("Test menu"), "Escape from the page went to " + title(context));
			clickWidget(context, 2);
			context.waitTicks(3);
			context.getInput().pressKey(InputConstants.KEY_G);
			context.waitTicks(3);
			check(screenName(context).equals("none"), "the key left " + screenName(context) + " open");
			context.getInput().pressKey(InputConstants.KEY_G);
			context.waitTicks(3);
			clickWidget(context, 2);
			context.waitTicks(3);
			int fromPage = ActionMenuProbe.RUNS.get();
			clickWidget(context, 0);
			context.waitTicks(5);
			check(ActionMenuProbe.RUNS.get() == fromPage + 1 && screenName(context).equals("none"),
				"the page's command button did not run and close");

			// The mods menu's entry opens the editor.
			server.runOnServer(s -> ServerPlayNetworking.send(connection.getServerPlayer(),
				new ClientSettingS2C("pandorical", "actionMenus", "edit")));
			context.waitTicks(5);
			check(screenName(context).endsWith("MenuListScreen"), "the mods menu entry opened " + screenName(context));
			context.takeScreenshot("action-menu-editor");

			// Through the editor as a player goes: the menu, its first button, a new icon, and a
			// key instead of a command.
			// By name, not by row: the server offers menus of its own now, and they sort above
			// the player's, so the first row has not been this test's menu since.
			clickLabelled(context, "Test menu");
			context.waitTicks(2);
			check(screenName(context).endsWith("MenuEditScreen"), "the menu row opened " + screenName(context));
			clickWidget(context, 2);
			context.waitTicks(2);
			check(screenName(context).endsWith("ButtonEditScreen"), "the first button opened " + screenName(context));
			clickWidget(context, 0);
			context.waitTicks(2);
			check(screenName(context).endsWith("ItemPickerScreen"), "the icon opened " + screenName(context));
			context.getInput().typeChars("diamond sword");
			context.waitTicks(2);
			clickWidget(context, 1);
			context.waitTicks(2);
			check(screenName(context).endsWith("ButtonEditScreen"), "picking an item went to " + screenName(context));
			clickWidget(context, 4);
			context.waitTicks(2);
			clickWidget(context, 5);
			context.waitTicks(2);
			check(screenName(context).endsWith("KeyPickerScreen"), "the key chooser opened " + screenName(context));
			context.getInput().typeChars("jump");
			context.waitTicks(2);
			clickWidget(context, 1);
			context.waitTicks(2);
			context.takeScreenshot("action-menu-button");
			clickWidget(context, 8);
			context.waitTicks(2);
			check(screenName(context).endsWith("MenuEditScreen"), "Save went to " + screenName(context));
			String saved = context.computeOnClient(client -> {
				ActionMenus.Entry first = ActionMenus.mine().get(0).buttons.get(0);
				return first.icon + " " + first.type + " " + first.keyMapping;
			});
			check(saved.equals("minecraft:diamond_sword key key.jump"), "the button saved as " + saved);

			context.runOnClient(client -> {
				client.gui.setScreen(null);
				ActionMenus.mine().clear();
				ActionMenus.save();
			});
			// The editor pauses a single-player game; closing the world while it is still coming
			// out of that pause deadlocks the test harness's own tick lock.
			context.waitTicks(10);
		}
	}

	private static String title(ClientGameTestContext context) {
		return context.computeOnClient(client -> client.gui.screen() == null ? "none"
			: client.gui.screen().getTitle().getString());
	}

	private static String screenName(ClientGameTestContext context) {
		return context.computeOnClient(client -> client.gui.screen() == null ? "none"
			: client.gui.screen().getClass().getSimpleName());
	}

	/** A click on the widget whose label starts with this, whatever row it has ended up in. */
	private static void clickLabelled(ClientGameTestContext context, String label) {
		int index = context.computeOnClient(client -> {
			var children = client.gui.screen().children();
			for (int i = 0; i < children.size(); i++) {
				if (children.get(i) instanceof AbstractWidget widget
						&& widget.getMessage().getString().startsWith(label)) {
					return i;
				}
			}
			return -1;
		});
		if (index < 0) throw new AssertionError("no widget labelled " + label);
		clickWidget(context, index);
	}

	/** A click in the middle of the screen's n-th widget, as the mouse would make it. */
	private static void clickWidget(ClientGameTestContext context, int n) {
		double[] at = context.computeOnClient(client -> {
			AbstractWidget widget = (AbstractWidget) client.gui.screen().children().get(n);
			double scale = client.getWindow().getGuiScale();
			return new double[] {(widget.getX() + widget.getWidth() / 2.0) * scale,
				(widget.getY() + widget.getHeight() / 2.0) * scale};
		});
		context.getInput().setCursorPos(at[0], at[1]);
		context.waitTick();
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
	}

	private static void check(boolean holds, String otherwise) {
		if (!holds) throw new AssertionError(otherwise);
	}
}
