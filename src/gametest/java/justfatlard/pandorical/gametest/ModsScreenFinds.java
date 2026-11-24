package justfatlard.pandorical.gametest;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import justfatlard.pandorical.api.NavigableScreen;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.settings.ClientMods;
import justfatlard.pandorical.settings.ModCatalog;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;

/**
 * The mods screen does what a mod list is for: each mod's licence, links and icon reach it, a
 * search narrows the list and widening it brings every row back, and a link asks before it opens.
 */
public final class ModsScreenFinds implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "pandorical", session -> {
			AtomicReference<String> wrong = new AtomicReference<>();
			session.onServer(server -> {
				var mine = ClientMods.aboutOf(session.player(), "pandorical");
				var ours = ModCatalog.about("pandorical");
				if (mine == null) wrong.set("the client never reported Pandorical's licence, icon and links");
				else if (!mine.icon().equals("pandorical:icon.png")) wrong.set("the client reported Pandorical's icon as '" + mine.icon() + "'");
				else if (ours == null || !ours.license().equals("MIT")) wrong.set("the server read Pandorical's licence as " + (ours == null ? null : ours.license()));
				else if (ours.links().stream().noneMatch(link -> link.label().equals("Homepage") && link.url().startsWith("https://github.com/")))
					wrong.set("Pandorical's homepage is not among its links: " + ours.links());
			});
			check(wrong.get() == null, wrong.get());
			if (!session.full()) return;

			session.onServer(server -> PandoricalApi.settings().open(session.player()));
			session.waitTicks(10);
			check(regions(context).contains("mod:pandorical"), "the mods screen did not list Pandorical: " + regions(context));

			click(context, "search");
			context.getInput().typeChars("no mod is called this");
			session.waitTicks(10);
			check(!regions(context).contains("mod:pandorical"), "a search matching nothing still listed Pandorical");

			for (int i = 0; i < "no mod is called this".length(); i++) {
				context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_BACKSPACE);
			}
			context.getInput().typeChars("pandor");
			session.waitTicks(10);
			check(regions(context).contains("mod:pandorical"), "searching for Pandorical by part of its name did not list it");
			check(regions(context).contains("folder"), "the mods screen had no button for the mods folder");

			// A mod of the player's own with a config screen declared for Mod Menu: Configure opens it
			click(context, "search");
			for (int i = 0; i < "pandor".length(); i++) context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_BACKSPACE);
			session.waitTicks(10);
			click(context, "mod:pandorical-gametest");
			session.waitTicks(10);
			check(regions(context).contains("configure"), "the test mod's page has no Configure button: " + regions(context));
			click(context, "configure");
			session.waitTicks(10);
			check(context.computeOnClient(client -> client.gui.screen() instanceof GametestModMenu.Config),
				"Configure on the server's mods screen did not open the mod's config screen");
			context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
			session.waitTicks(10);

			click(context, "mod:pandorical");
			session.waitTicks(10);
			click(context, "tab:readme");
			session.waitTicks(10);
			check(regions(context).contains("link:0"), "Pandorical's readme tab shows no links: " + regions(context));
			click(context, "link:0");
			session.waitTicks(10);
			check(context.computeOnClient(client -> client.gui.screen() instanceof ConfirmLinkScreen),
				"pressing a link did not ask before opening it");
			context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
			session.waitTicks(5);
			context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
			session.waitTicks(5);
		});
	}

	private static List<String> regions(ClientGameTestContext context) {
		return context.computeOnClient(client -> client.gui.screen() instanceof NavigableScreen navigable
			? navigable.navRegions().stream().map(NavigableScreen.NavRegion::id).toList()
			: List.of());
	}

	private static void click(ClientGameTestContext context, String componentId) {
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

	private static void check(boolean ok, String complaint) {
		if (!ok) throw new AssertionError(complaint);
	}
}
