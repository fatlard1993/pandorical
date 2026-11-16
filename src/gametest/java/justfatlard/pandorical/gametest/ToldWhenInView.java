package justfatlard.pandorical.gametest;

import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.client.animation.EntityAnimations;
import justfatlard.pandorical.client.renderer.ClientBlockMarks;
import justfatlard.pandorical.client.renderer.ClientBlockShapes;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Block marks, block shapes and animations reach a client only where it can see them, and keep
 * up with it as it goes: nothing for a place far off, everything there once the player arrives,
 * an animation picked up part way through rather than started over, changes while the player is
 * there, all of it forgotten as the player leaves, and nothing stale on the way back.
 */
public final class ToldWhenInView implements FabricClientGameTest {
	private static final String MARK = "pandorical-gametest:far";
	private static final String ANIMATION = "pandorical-gametest:wave";
	/** Ticks to wait on a long teleport, which may have the chunks there to generate. */
	private static final int ARRIVAL = 1200;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!Smoke.full()) return;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			server.waitFor(s -> PandoricalApi.isAvailable(connection.getServerPlayer()));
			server.runCommand("gamemode spectator @a");

			BlockPos home = server.computeOnServer(s -> connection.getServerPlayer().blockPosition());
			BlockPos far = home.offset(2048, 0, 2048);
			server.runCommand("forceload add " + far.getX() + " " + far.getZ());
			int standId = server.computeOnServer(s -> {
				s.overworld().getChunk(far);
				ArmorStand stand = new ArmorStand(EntityTypes.ARMOR_STAND, s.overworld());
				stand.setPos(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
				stand.setNoGravity(true);
				s.overworld().addFreshEntity(stand);
				PandoricalApi.blockMarks().mark(s.overworld(), far, MARK);
				PandoricalApi.blockShapes().setShape(s.overworld(), far, List.of(new AABB(0, 0, 0, 1, 0.5, 1)));
				PandoricalApi.animations().play(stand, ANIMATION, true);
				return stand.getId();
			});
			long playedAt = System.currentTimeMillis();

			try {
				// Far off: none of it is sent.
				context.waitFor(client -> System.currentTimeMillis() - playedAt > 1500);
				check(!context.computeOnClient(client -> ClientBlockMarks.has(far, MARK)), "a mark two thousand blocks off was sent");
				check(context.computeOnClient(client -> ClientBlockShapes.at(far)) == null, "a shape two thousand blocks off was sent");
				check(context.computeOnClient(client -> EntityAnimations.playing(standId)) == null, "an animation on an entity out of view was sent");

				// Arrived: all of it, the animation as far in as it has got.
				tp(server, far);
				context.waitFor(client -> ClientBlockMarks.has(far, MARK) && ClientBlockShapes.at(far) != null
					&& EntityAnimations.playing(standId) != null, ARRIVAL);
				long startedAt = context.computeOnClient(client -> EntityAnimations.playing(standId).startedAt());
				check(Math.abs(startedAt - playedAt) < 500,
					"the animation started over on arrival, " + (startedAt - playedAt) + " ms after it began");

				// Changes while the player is there reach them.
				server.runOnServer(s -> PandoricalApi.blockMarks().unmark(s.overworld(), far, MARK));
				context.waitFor(client -> !ClientBlockMarks.has(far, MARK));
				server.runOnServer(s -> PandoricalApi.blockMarks().mark(s.overworld(), far, MARK));
				context.waitFor(client -> ClientBlockMarks.has(far, MARK));

				// Left: forgotten with the chunk and the entity.
				tp(server, home);
				context.waitFor(client -> !ClientBlockMarks.has(far, MARK) && ClientBlockShapes.at(far) == null
					&& EntityAnimations.playing(standId) == null, ARRIVAL);

				// Cleared and stopped while away: nothing comes back on return.
				server.runOnServer(s -> {
					PandoricalApi.blockMarks().unmark(s.overworld(), far, MARK);
					PandoricalApi.blockShapes().clearShape(s.overworld(), far);
					PandoricalApi.animations().stop(s.overworld().getEntity(standId));
				});
				tp(server, far);
				context.waitFor(client -> client.level.getEntity(standId) != null, ARRIVAL);
				connection.waitForChunksRender();
				context.waitTicks(10);
				check(!context.computeOnClient(client -> ClientBlockMarks.has(far, MARK)), "a mark taken off while the player was away came back");
				check(context.computeOnClient(client -> ClientBlockShapes.at(far)) == null, "a shape cleared while the player was away came back");
				check(context.computeOnClient(client -> EntityAnimations.playing(standId)) == null, "an animation stopped while the player was away came back");
			} finally {
				server.runOnServer(s -> {
					PandoricalApi.blockMarks().unmark(s.overworld(), far, MARK);
					PandoricalApi.blockShapes().clearShape(s.overworld(), far);
					var stand = s.overworld().getEntity(standId);
					if (stand != null) stand.discard();
				});
				server.runCommand("forceload remove " + far.getX() + " " + far.getZ());
				tp(server, home);
			}
		}
	}

	private static void tp(TestServerContext server, BlockPos to) {
		server.runCommand("tp @a " + to.getX() + " " + to.getY() + " " + to.getZ());
	}

	private static void check(boolean holds, String otherwise) {
		if (!holds) throw new AssertionError(otherwise);
	}
}
