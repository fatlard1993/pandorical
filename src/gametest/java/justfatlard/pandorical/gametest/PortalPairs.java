package justfatlard.pandorical.gametest;

import justfatlard.pandorical.portal.PortalPairing;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * Two portals built near each other each get a nether portal of their own and each come back
 * home, where vanilla sends both through the one nearest.
 */
public final class PortalPairs implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			server.runCommand("gamemode creative @a");
			server.runOnServer(s -> PortalPairing.choose(s, true));

			BlockPos spawn = server.computeOnServer(s -> connection.getServerPlayer().blockPosition());
			BlockPos first = spawn.offset(4, 20, 0);
			BlockPos second = first.east(24);
			server.runOnServer(s -> {
				buildPortal(s.overworld(), first);
				buildPortal(s.overworld(), second);
			});

			Vec3 firstOut = travel(context, server, connection, Level.OVERWORLD, first, Level.NETHER);
			Vec3 firstHome = travel(context, server, connection, Level.NETHER, BlockPos.containing(firstOut), Level.OVERWORLD);
			check(firstHome.distanceTo(Vec3.atCenterOf(first)) < 4, "the first portal's way back came out at " + firstHome);

			Vec3 secondOut = travel(context, server, connection, Level.OVERWORLD, second, Level.NETHER);
			check(secondOut.distanceTo(firstOut) > 2.5,
				"the second portal came out of the first one's nether portal: " + secondOut + " and " + firstOut);
			Vec3 secondHome = travel(context, server, connection, Level.NETHER, BlockPos.containing(secondOut), Level.OVERWORLD);
			check(secondHome.distanceTo(Vec3.atCenterOf(second)) < 4, "the second portal's way back came out at " + secondHome);

			Vec3 firstAgain = travel(context, server, connection, Level.OVERWORLD, first, Level.NETHER);
			check(firstAgain.distanceTo(firstOut) < 2.5, "the first portal's way out moved to " + firstAgain);
		}
	}

	/** Stand in the portal at {@code entry} and wait to arrive; where the player arrived. */
	private static Vec3 travel(ClientGameTestContext context, TestServerContext server, TestServerConnection connection,
							   ResourceKey<Level> from, BlockPos entry, ResourceKey<Level> to) {
		context.waitTicks(40);
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			ServerLevel level = s.getLevel(from);
			BlockPos inside = portalBlockNear(level, entry);
			check(inside != null, "no portal at " + entry + " in " + from.identifier());
			// The cooldown from arriving would otherwise be renewed by standing in this portal.
			player.setPortalCooldown(0);
			player.teleportTo(level, inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5, Set.of(), 0, 0, true);
		});
		server.waitFor(s -> connection.getServerPlayer().level().dimension() == to, 400);
		context.waitTicks(10);
		return server.computeOnServer(s -> connection.getServerPlayer().position());
	}

	/** The nearest portal block: the other portal may be only a few blocks off. */
	private static BlockPos portalBlockNear(ServerLevel level, BlockPos around) {
		BlockPos nearest = null;
		for (BlockPos pos : BlockPos.betweenClosed(around.offset(-3, -3, -3), around.offset(3, 3, 3))) {
			if (!level.getBlockState(pos).is(Blocks.NETHER_PORTAL)) continue;
			if (nearest == null || pos.distSqr(around) < nearest.distSqr(around)) nearest = pos.immutable();
		}
		return nearest;
	}

	/** An obsidian frame around a 2 wide, 3 tall sheet facing north, its bottom-left portal block at {@code base}. */
	private static void buildPortal(ServerLevel level, BlockPos base) {
		for (int x = -1; x <= 2; x++) {
			for (int y = -1; y <= 3; y++) {
				BlockPos pos = base.offset(x, y, 0);
				boolean frame = x == -1 || x == 2 || y == -1 || y == 3;
				level.setBlock(pos, frame ? Blocks.OBSIDIAN.defaultBlockState()
					: Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.X), 18);
			}
		}
		for (int x = -1; x <= 2; x++) {
			level.setBlock(base.offset(x, -1, 1), Blocks.OBSIDIAN.defaultBlockState(), 18);
			level.setBlock(base.offset(x, -1, -1), Blocks.OBSIDIAN.defaultBlockState(), 18);
		}
	}

	private static void check(boolean holds, String otherwise) {
		if (!holds) throw new AssertionError(otherwise);
	}
}
