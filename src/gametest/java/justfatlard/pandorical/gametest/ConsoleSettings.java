package justfatlard.pandorical.gametest;

import java.util.ArrayList;
import java.util.List;
import justfatlard.pandorical.sleep.SleepSpeed;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.commands.CommandSource;
import net.minecraft.network.chat.Component;

/**
 * The server console, and RCON, which speaks as it, can change a server setting, and cannot change
 * a player's own.
 */
public final class ConsoleSettings implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "pandorical", session -> {
			List<String> said = new ArrayList<>();
			java.util.concurrent.atomic.AtomicBoolean on = new java.util.concurrent.atomic.AtomicBoolean();
			session.onServer(server -> {
				CommandSource console = new CommandSource() {
					@Override public void sendSystemMessage(Component message) { said.add(message.getString()); }
					@Override public boolean acceptsSuccess() { return true; }
					@Override public boolean acceptsFailure() { return true; }
					@Override public boolean shouldInformAdmins() { return false; }
				};
				var source = server.createCommandSourceStack().withSource(console);
				SleepSpeed.choose(server, false);
				server.getCommands().performPrefixedCommand(source, "pandorical settings pandorical sleepSpeedsNight true");
				server.getCommands().performPrefixedCommand(source, "pandorical settings pandorical showOnMaps false");
				server.getCommands().performPrefixedCommand(source, "pandorical settings");
				on.set(SleepSpeed.enabled(server));
			});
			String all = String.join(" | ", said);
			check(on.get(), "the console did not turn sleeping's speed-up on; it said: " + all);
			check(all.contains("Only server settings can be changed from the console"),
				"the console was not refused a player's own setting; it said: " + all);
			check(all.contains("(server, ops only)"), "listing from the console showed no server settings; it said: " + all);
			session.onServer(server -> SleepSpeed.choose(server, false));
		});
	}

	private static void check(boolean ok, String complaint) {
		if (!ok) throw new AssertionError(complaint);
	}
}
