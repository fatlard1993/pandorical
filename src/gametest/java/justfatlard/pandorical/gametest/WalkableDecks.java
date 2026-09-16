package justfatlard.pandorical.gametest;

import justfatlard.pandorical.api.BlockEntry;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.RelPos;
import justfatlard.pandorical.api.StructurePose;
import justfatlard.pandorical.client.structure.StructureManager;
import justfatlard.pandorical.client.structure.StructureManager.ClientStructure;
import justfatlard.pandorical.client.structure.StructureManager.StructurePoseSnapshot;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A player stood on a walkable structure stays where they stood on it while it sails and turns,
 * walks across it, and is still standing a moment after it is hidden.
 */
public final class WalkableDecks implements FabricClientGameTest {
	private static final String ID = "pandorical-gametest:deck";
	private static final double SPEED = 0.45;
	private static final float TURN = 1.5f;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			server.waitFor(s -> PandoricalApi.isAvailable(connection.getServerPlayer()));
			server.runCommand("gamemode survival @a");

			BlockPos spawn = server.computeOnServer(s -> connection.getServerPlayer().blockPosition());
			Vec3 origin = new Vec3(spawn.getX(), spawn.getY() + 30, spawn.getZ());

			server.runOnServer(s -> {
				ArmorStand anchor = new ArmorStand(EntityTypes.ARMOR_STAND, s.overworld());
				anchor.setPos(origin);
				anchor.setNoGravity(true);
				anchor.setInvisible(true);
				s.overworld().addFreshEntity(anchor);

				List<BlockEntry> deck = new ArrayList<>();
				for (int x = -4; x <= 4; x++) {
					for (int z = -4; z <= 4; z++) {
						deck.add(new BlockEntry(new RelPos(x, 0, z), Blocks.OAK_PLANKS.defaultBlockState()));
					}
				}
				PandoricalApi.structures().spawn(anchor, ID, deck, new StructurePose(origin.x, origin.y, origin.z, 0));
				PandoricalApi.structures().setWalkable(ID, true);
			});
			context.waitFor(client -> deck() != null && deck().walkable);

			server.runCommand("tp @a " + (origin.x + 0.5) + " " + (origin.y + 1) + " " + (origin.z + 0.5));
			context.waitTicks(10);
			Vec3 stood = local(context);
			check(Math.abs(stood.y - 1) < 0.01, "the player did not come to rest on the deck: local " + stood);

			// Sail and turn it for five seconds; they should not have moved on it at all.
			for (int tick = 1; tick <= 100; tick++) {
				double x = origin.x + SPEED * tick;
				float yaw = TURN * tick;
				server.runOnServer(s -> PandoricalApi.structures().updatePose(ID, new StructurePose(x, origin.y, origin.z, yaw)));
				context.waitTick();
				Vec3 now = local(context);
				check(now.distanceTo(stood) < 0.05,
					"tick " + tick + ": the player moved on the deck from " + stood + " to " + now);
			}

			// Walking across it, still sailing.
			context.getInput().holdKey(options -> options.keyUp);
			for (int tick = 101; tick <= 112; tick++) {
				double x = origin.x + SPEED * tick;
				float yaw = TURN * tick;
				server.runOnServer(s -> PandoricalApi.structures().updatePose(ID, new StructurePose(x, origin.y, origin.z, yaw)));
				context.waitTick();
			}
			context.getInput().releaseKey(options -> options.keyUp);
			Vec3 walked = local(context);
			check(Math.abs(walked.y - 1) < 0.01, "walking, the player left the deck's surface: local " + walked);
			check(Math.hypot(walked.x - stood.x, walked.z - stood.z) > 1.0, "walking forward moved the player only to " + walked);

			// Hidden, it holds them up until the blocks that replace it arrive.
			double feet = context.computeOnClient(client -> client.player.getY());
			server.runOnServer(s -> PandoricalApi.structures().setVisible(ID, false));
			context.waitTicks(5);
			double after = context.computeOnClient(client -> client.player.getY());
			check(Math.abs(after - feet) < 0.01, "hidden, the deck dropped the player from " + feet + " to " + after);

			server.runOnServer(s -> PandoricalApi.structures().despawn(ID));
		}
	}

	private static ClientStructure deck() {
		for (ClientStructure structure : StructureManager.getActive()) {
			if (structure.blocks.size() == 81) return structure;
		}
		return null;
	}

	/** The player's feet in the deck's own frame, as the client has it. */
	private static Vec3 local(ClientGameTestContext context) {
		return context.computeOnClient(client -> {
			LocalPlayer player = client.player;
			StructurePoseSnapshot pose = deck().thisTick();
			double yaw = Math.toRadians(pose.yaw());
			double dx = player.getX() - pose.x();
			double dz = player.getZ() - pose.z();
			return new Vec3(dx * Math.cos(yaw) + dz * Math.sin(yaw), player.getY() - pose.y(),
				-dx * Math.sin(yaw) + dz * Math.cos(yaw));
		});
	}

	private static void check(boolean holds, String otherwise) {
		if (!holds) throw new AssertionError(otherwise);
	}
}
