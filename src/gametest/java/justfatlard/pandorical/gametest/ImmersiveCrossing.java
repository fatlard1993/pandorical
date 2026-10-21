package justfatlard.pandorical.gametest;

import justfatlard.pandorical.client.portal.Crossing;
import justfatlard.pandorical.portal.ImmersivePortals;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;

import java.util.Set;

/**
 * With immersive portals on, a survival player is through a nether portal in about half a second,
 * not four, with the swirl full as they go and still thinning on the far side. Off, the wait is
 * vanilla's again.
 */
public final class ImmersiveCrossing implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			server.runCommand("gamemode survival @a");
			BlockPos spawn = server.computeOnServer(s -> connection.getServerPlayer().blockPosition());
			BlockPos portal = spawn.offset(4, 20, 0);
			server.runOnServer(s -> buildPortal(s.overworld(), portal));

			server.runOnServer(s -> ImmersivePortals.choose(s, true));
			context.waitTicks(5);
			check(context.computeOnClient(client -> Crossing.active()), "the client was not told the server's portals are immersive");

			int took = cross(context, server, connection, Level.OVERWORLD, portal, Level.NETHER, "immersive-crossing-in");
			check(took <= ImmersivePortals.WAIT_TICKS + 10, "an immersive portal took " + took + " ticks");
			float swirl = context.computeOnClient(client -> client.player.portalEffectIntensity);
			check(swirl > 0.5F, "the swirl was " + swirl + " on arrival, not still thick");
			context.takeScreenshot(TestScreenshotOptions.of("immersive-crossing-out").disableCounterPrefix());
			context.waitTicks(40);
			swirl = context.computeOnClient(client -> client.player.portalEffectIntensity);
			check(swirl == 0.0F, "the swirl was still " + swirl + " two seconds after arriving");

			server.runOnServer(s -> ImmersivePortals.choose(s, false));
			context.waitTicks(5);
			check(!context.computeOnClient(client -> Crossing.active()), "the client still thinks portals are immersive");
			BlockPos home = server.computeOnServer(s -> connection.getServerPlayer().blockPosition());
			took = cross(context, server, connection, Level.NETHER, home, Level.OVERWORLD, null);
			check(took >= 70, "with immersive portals off, a portal took only " + took + " ticks");
		}
	}

	/** Stand in the portal near {@code entry} and wait to arrive; how many ticks it took. */
	private static int cross(ClientGameTestContext context, TestServerContext server, TestServerConnection connection,
							 ResourceKey<Level> from, BlockPos entry, ResourceKey<Level> to, String shot) {
		context.waitTicks(40);
		int start = server.computeOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			ServerLevel level = s.getLevel(from);
			BlockPos inside = portalBlockNear(level, entry);
			check(inside != null, "no portal at " + entry + " in " + from.identifier());
			player.setPortalCooldown(0);
			player.teleportTo(level, inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5, Set.of(), 0, 0, true);
			return s.getTickCount();
		});
		if (shot != null) {
			context.waitTicks(ImmersivePortals.WAIT_TICKS / 2);
			context.takeScreenshot(TestScreenshotOptions.of(shot).disableCounterPrefix());
		}
		server.waitFor(s -> connection.getServerPlayer().level().dimension() == to, 400);
		int took = server.computeOnServer(s -> s.getTickCount()) - start;
		context.waitTicks(2);
		return took;
	}

	private static BlockPos portalBlockNear(ServerLevel level, BlockPos around) {
		for (BlockPos pos : BlockPos.betweenClosed(around.offset(-3, -3, -3), around.offset(3, 3, 3))) {
			if (level.getBlockState(pos).is(Blocks.NETHER_PORTAL)) return pos.immutable();
		}
		return null;
	}

	/** An obsidian frame around a 2 wide, 3 tall sheet, its bottom-left portal block at {@code base}, on a ledge. */
	private static void buildPortal(ServerLevel level, BlockPos base) {
		for (int x = -1; x <= 2; x++) {
			for (int y = -1; y <= 3; y++) {
				boolean frame = x == -1 || x == 2 || y == -1 || y == 3;
				level.setBlock(base.offset(x, y, 0), frame ? Blocks.OBSIDIAN.defaultBlockState()
					: Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.X), 18);
			}
			level.setBlock(base.offset(x, -1, 1), Blocks.OBSIDIAN.defaultBlockState(), 18);
			level.setBlock(base.offset(x, -1, -1), Blocks.OBSIDIAN.defaultBlockState(), 18);
		}
	}

	private static void check(boolean holds, String otherwise) {
		if (!holds) throw new AssertionError(otherwise);
	}
}
