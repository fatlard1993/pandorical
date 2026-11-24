package justfatlard.pandorical.gametest;

import justfatlard.pandorical.client.mods.ModListScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/**
 * The client's own mod list, with no server at all: a Mods button on the title screen, a search
 * that narrows the list, and Configure opening a config screen a mod declared for Mod Menu, which
 * returns to the list when it closes.
 */
public final class ModListOffline implements FabricClientGameTest {
	private static final String TEST_MOD = "Pandorical launch gate";

	@Override
	public void runTest(ClientGameTestContext context) {
		context.setScreen(TitleScreen::new);
		context.waitTicks(2);
		boolean modMenu = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("modmenu");
		check(pandoricalsButton(context) != modMenu, modMenu
			? "with Mod Menu installed, Pandorical still put its Mods button on the title screen"
			: "the title screen has no Mods button");
		if (modMenu) {
			System.out.println("[pandorical test] Mod Menu installed; its API loaded from "
				+ com.terraformersmc.modmenu.api.ModMenuApi.class.getProtectionDomain().getCodeSource().getLocation());
		}
		if (modMenu && Smoke.full()) {
			// By reflection: Mod Menu is not on the compile classpath
			String verdict = context.computeOnClient(client -> {
				try {
					Class<?> modMenuClass = Class.forName("com.terraformersmc.modmenu.ModMenu");
					Object config = modMenuClass.getMethod("getConfigScreen", String.class, net.minecraft.client.gui.screens.Screen.class)
						.invoke(null, "pandorical-gametest", new TitleScreen());
					if (!(config instanceof GametestModMenu.Config)) return "Mod Menu did not find the test mod's config screen, got " + config;
					// Whichever copy of the API loaded answers as Mod Menu would
					var bare = new com.terraformersmc.modmenu.api.ModMenuApi() {}.getModConfigScreenFactory();
					if (!bare.getClass().getName().equals("com.terraformersmc.modmenu.util.NullScreenFactory"))
						return "a mod with no config screen gets " + bare.getClass().getName() + ", which Mod Menu offers as one";
					Object text = modMenuClass.getMethod("createModsButtonText", boolean.class).invoke(null, true);
					if (!com.terraformersmc.modmenu.api.ModMenuApi.createModsButtonText().getString().equals(((Component) text).getString()))
						return "the Mods button text is not Mod Menu's";
					var list = com.terraformersmc.modmenu.api.ModMenuApi.createModsScreen(new TitleScreen());
					if (!list.getClass().getName().equals("com.terraformersmc.modmenu.gui.ModsScreen"))
						return "createModsScreen gave " + list.getClass().getName() + ", not Mod Menu's list";
					client.gui.setScreen(list);
					return null;
				} catch (ReflectiveOperationException | LinkageError e) {
					return "Mod Menu broke on Pandorical's copy of its API: " + e;
				}
			});
			check(verdict == null, verdict);
			// Mod Menu bundles Placeholder API inside its jar: the mods screens name it
			var about = justfatlard.pandorical.settings.ModCatalog.aboutOf(
				net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("modmenu").orElseThrow());
			check(about.bundled().contains("Placeholder API"), "Mod Menu's bundled mods read as '" + about.bundled() + "'");
			context.waitTicks(5);
			check(context.computeOnClient(client -> client.gui.screen() != null
				&& client.gui.screen().getClass().getName().equals("com.terraformersmc.modmenu.gui.ModsScreen")),
				"Mod Menu's own list did not stay open");
		}
		if (!Smoke.full()) return;

		check(context.computeOnClient(client -> {
			var about = justfatlard.pandorical.settings.ModCatalog.aboutOf(
				net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("pandorical").orElseThrow());
			var id = net.minecraft.resources.Identifier.tryParse(about.icon());
			return id != null && client.getResourceManager().getResource(id).isPresent();
		}), "Pandorical's icon does not resolve to a texture the game has");
		context.setScreen(() -> new ModListScreen(new TitleScreen()));
		context.waitTicks(2);
		click(context, TEST_MOD);
		context.waitTicks(2);
		click(context, "Configure");
		context.waitTicks(2);
		check(context.computeOnClient(client -> client.gui.screen() instanceof GametestModMenu.Config),
			"Configure did not open the config screen the mod declared for Mod Menu");
		context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
		context.waitTicks(2);
		check(context.computeOnClient(client -> client.gui.screen() instanceof ModListScreen),
			"closing the config screen did not come back to the mod list");

		click(context, searchBoxCentre(context));
		context.getInput().typeChars("no mod is called this");
		context.waitTicks(2);
		check(widget(context, TEST_MOD) == null, "a search matching nothing still listed " + TEST_MOD);
		for (int i = 0; i < "no mod is called this".length(); i++) {
			context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_BACKSPACE);
		}
		context.getInput().typeChars("launch");
		context.waitTicks(2);
		check(widget(context, TEST_MOD) != null, "searching part of its name did not list " + TEST_MOD);
		context.setScreen(TitleScreen::new);
	}

	/** Pandorical's own Mods button, in the title screen's top right corner. */
	private static boolean pandoricalsButton(ClientGameTestContext context) {
		return context.computeOnClient(client -> {
			var screen = client.gui.screen();
			for (var child : screen.children()) {
				if (child instanceof AbstractWidget w && w.getX() == screen.width - 116 && w.getY() == 6) return true;
			}
			return false;
		});
	}

	private static double[] searchBoxCentre(ClientGameTestContext context) {
		return context.computeOnClient(client -> {
			for (var child : client.gui.screen().children()) {
				if (child instanceof EditBox box) {
					double scale = client.getWindow().getGuiScale();
					return new double[] {(box.getX() + box.getWidth() / 2.0) * scale, (box.getY() + box.getHeight() / 2.0) * scale};
				}
			}
			throw new AssertionError("the mod list has no search box");
		});
	}

	private static void click(ClientGameTestContext context, double[] at) {
		context.getInput().setCursorPos(at[0], at[1]);
		context.waitTick();
		context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
	}

	private static double[] widget(ClientGameTestContext context, String label) {
		return context.computeOnClient(client -> {
			for (var child : client.gui.screen().children()) {
				// The selected row is marked ahead of its name
				if (child instanceof AbstractWidget w && w.visible && w.getMessage().getString().replace("\u25B6 ", "").equals(label)) {
					double scale = client.getWindow().getGuiScale();
					return new double[] {(w.getX() + w.getWidth() / 2.0) * scale, (w.getY() + w.getHeight() / 2.0) * scale};
				}
			}
			return null;
		});
	}

	private static void click(ClientGameTestContext context, String label) {
		double[] at = widget(context, label);
		if (at == null) throw new AssertionError("no button " + label + " on the screen");
		context.getInput().setCursorPos(at[0], at[1]);
		context.waitTick();
		context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
	}

	private static void check(boolean ok, String complaint) {
		if (!ok) throw new AssertionError(complaint);
	}
}
