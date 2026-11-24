package justfatlard.pandorical.gametest;

import justfatlard.pandorical.sleep.SleepSpeed;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Sleeping with the option on speeds the overworld's clock up to the chosen speed instead of skipping
 * the night, and puts it back once the sleeper wakes at morning; with it off, the night is skipped as
 * vanilla skips it; and a clock left sped up is put back.
 */
public final class SleepSpeeds implements FabricClientGameTest {
	private static final int FASTEST = 20;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!Smoke.full()) return;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			server.runCommand("difficulty peaceful");
			BlockPos bed = server.computeOnServer(s -> connection.getServerPlayer().blockPosition().east(3));
			server.runOnServer(s -> placeBed(s, bed));

			server.runOnServer(s -> {
				SleepSpeed.choose(s, true);
				SleepSpeed.chooseFastest(s, FASTEST);
			});
			server.runCommand("time set midnight");
			sleep(server, connection, bed);
			context.waitTicks(110);
			check(server.computeOnServer(s -> rate(s)) == FASTEST, "with everyone asleep, the clock runs " + FASTEST + " times faster");
			check(server.computeOnServer(s -> connection.getServerPlayer().isSleeping()), "the night was skipped rather than sped up");
			context.waitFor(client -> server.computeOnServer(s -> !connection.getServerPlayer().isSleeping()), 2400);
			context.waitTicks(5);
			check(server.computeOnServer(s -> rate(s)) == 1.0F, "once the sleeper woke, the clock runs at its own speed again");

			server.runOnServer(s -> SleepSpeed.choose(s, false));
			server.runCommand("time set midnight");
			sleep(server, connection, bed);
			context.waitFor(client -> server.computeOnServer(s -> !connection.getServerPlayer().isSleeping()), 400);
			check(server.computeOnServer(s -> rate(s)) == 1.0F, "with the option off, the clock was sped up");

			// Sped up and recorded, then the sleeper gone with no tick between: what a crash mid-night leaves
			server.runOnServer(s -> SleepSpeed.choose(s, true));
			server.runCommand("time set midnight");
			sleep(server, connection, bed);
			context.waitTicks(110);
			server.runOnServer(s -> {
				connection.getServerPlayer().stopSleepInBed(true, true);
				SleepSpeed.restoreLeftOver(s);
			});
			check(server.computeOnServer(s -> rate(s)) == 1.0F, "a clock left sped up was put back at start");
		}
	}

	private static void placeBed(MinecraftServer server, BlockPos foot) {
		BlockState bed = Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
		server.overworld().setBlockAndUpdate(foot, bed.setValue(BlockStateProperties.BED_PART, BedPart.FOOT));
		server.overworld().setBlockAndUpdate(foot.east(), bed.setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
	}

	private static void sleep(TestServerContext server, TestServerConnection connection, BlockPos foot) {
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			check(player.startSleeping(foot.east()), "the player lay down in the bed");
		});
	}

	private static Holder<WorldClock> clock(MinecraftServer server) {
		return server.overworld().dimensionType().defaultClock().orElseThrow();
	}

	private static float rate(MinecraftServer server) {
		return server.clockManager().getInstance(clock(server)).rate();
	}

	private static void check(boolean holds, String otherwise) {
		if (!holds) throw new AssertionError(otherwise);
	}
}
