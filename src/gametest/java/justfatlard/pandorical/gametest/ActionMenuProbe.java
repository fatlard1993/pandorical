package justfatlard.pandorical.gametest;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import justfatlard.pandorical.api.ActionMenuApi;
import justfatlard.pandorical.api.PandoricalApi;
import net.minecraft.commands.Commands;

import java.util.List;

import java.util.concurrent.atomic.AtomicInteger;

/** A command any player may run, counting how often it was: what an action menu button sends. */
public final class ActionMenuProbe implements ModInitializer {
	static final AtomicInteger RUNS = new AtomicInteger();

	/** What a mod promoting a button looks like, for the test that checks one arrives. */
	static final String MENU_ID = "pandorical-gametest:probe";

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
			dispatcher.register(Commands.literal("actionmenuprobe").executes(context -> RUNS.incrementAndGet())));

		PandoricalApi.actionMenus().suggestMenu(MENU_ID, "Probe menu", List.of(
			ActionMenuApi.Button.runs("minecraft:paper", "Probe", "actionmenuprobe")));
		PandoricalApi.actionMenus().suggestButton(
			ActionMenuApi.Button.runs("minecraft:stone", "Probe again", "actionmenuprobe"));
	}
}
