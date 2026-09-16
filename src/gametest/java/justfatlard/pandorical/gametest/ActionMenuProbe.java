package justfatlard.pandorical.gametest;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;

import java.util.concurrent.atomic.AtomicInteger;

/** A command any player may run, counting how often it was: what an action menu button sends. */
public final class ActionMenuProbe implements ModInitializer {
	static final AtomicInteger RUNS = new AtomicInteger();

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
			dispatcher.register(Commands.literal("actionmenuprobe").executes(context -> RUNS.incrementAndGet())));
	}
}
