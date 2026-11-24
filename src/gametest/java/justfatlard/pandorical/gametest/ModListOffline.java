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
		// With Mod Menu installed its own button is there and Pandorical's stays away
		if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("modmenu")) {
			String mods = Component.translatable("pandorical.mods.button").getString();
			check(widget(context, mods) != null, "the title screen has no " + mods + " button");
		} else {
			System.out.println("[pandorical test] Mod Menu installed; its API loaded from "
				+ com.terraformersmc.modmenu.api.ModMenuApi.class.getProtectionDomain().getCodeSource().getLocation());
		}
		if (!Smoke.full()) return;

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

		search(context, "no mod is called this");
		context.waitTicks(2);
		check(widget(context, TEST_MOD) == null, "a search matching nothing still listed " + TEST_MOD);
		search(context, "launch");
		context.waitTicks(2);
		check(widget(context, TEST_MOD) != null, "searching part of its name did not list " + TEST_MOD);
		context.setScreen(TitleScreen::new);
	}

	/** Found first and typed into after: typing rebuilds the screen, which the loop would be walking. */
	private static void search(ClientGameTestContext context, String text) {
		context.runOnClient(client -> {
			EditBox found = null;
			for (var child : client.gui.screen().children()) {
				if (child instanceof EditBox box) found = box;
			}
			if (found == null) throw new AssertionError("the mod list has no search box");
			found.setValue(text);
		});
	}

	private static double[] widget(ClientGameTestContext context, String label) {
		return context.computeOnClient(client -> {
			for (var child : client.gui.screen().children()) {
				if (child instanceof AbstractWidget w && w.visible && w.getMessage().getString().equals(label)) {
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
