package justfatlard.pandorical.gametest;

import justfatlard.pandorical.api.BlockEntry;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.RelPos;
import justfatlard.pandorical.api.StructurePose;
import justfatlard.pandorical.client.structure.StructureManager;
import justfatlard.pandorical.client.structure.StructureManager.ClientStructure;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A player in the dry hold of a structure standing in water is in air, to the server and to their
 * client, camera included, and does not run out of it; with the hold no longer dry, the same
 * player in the same place is under water, which is what shows the checks can fail.
 */
public final class DryHolds implements FabricClientGameTest {
	private static final String ID = "pandorical-gametest:hold";
	/** The hold: a floor and four walls, the inside {@code -2..2} across and {@code 1..4} up. */
	private static final int HALF = 3;
	private static final int TOP = 4;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			server.waitFor(s -> PandoricalApi.isAvailable(connection.getServerPlayer()));
			server.runCommand("gamemode survival @a");

			BlockPos spawn = server.computeOnServer(s -> connection.getServerPlayer().blockPosition());
			int ox = spawn.getX(), oy = spawn.getY() + 30, oz = spawn.getZ();
			// Water over all of it, hull and hold, to a surface a block below the walls' tops.
			server.runCommand("fill " + (ox - HALF - 1) + " " + oy + " " + (oz - HALF - 1) + " "
				+ (ox + HALF + 1) + " " + (oy + TOP - 1) + " " + (oz + HALF + 1) + " water");

			List<RelPos> dry = new ArrayList<>();
			server.runOnServer(s -> {
				ArmorStand anchor = new ArmorStand(EntityTypes.ARMOR_STAND, s.overworld());
				anchor.setPos(ox, oy, oz);
				anchor.setNoGravity(true);
				anchor.setInvisible(true);
				s.overworld().addFreshEntity(anchor);

				List<BlockEntry> hull = new ArrayList<>();
				for (int x = -HALF; x <= HALF; x++) {
					for (int z = -HALF; z <= HALF; z++) {
						for (int y = 0; y <= TOP; y++) {
							boolean wall = y == 0 || Math.abs(x) == HALF || Math.abs(z) == HALF;
							if (wall) hull.add(new BlockEntry(new RelPos(x, y, z), Blocks.OAK_PLANKS.defaultBlockState()));
							if (y < TOP) dry.add(new RelPos(x, y, z));
						}
					}
				}
				PandoricalApi.structures().spawn(anchor, ID, hull, new StructurePose(ox, oy, oz, 0));
				PandoricalApi.structures().setWalkable(ID, true);
				PandoricalApi.structures().setDry(ID, dry);
			});
			context.waitFor(client -> hold() != null && !hold().dry.isEmpty());

			server.runCommand("tp @a " + (ox + 0.5) + " " + (oy + 1) + " " + (oz + 0.5));
			context.waitTicks(40);

			check(!context.computeOnClient(client -> client.player.isInWater()), "the client has the player in water in a dry hold");
			check(!context.computeOnClient(client -> client.player.isUnderWater()), "the client has the player under water in a dry hold");
			check(context.computeOnClient(client -> client.gameRenderer.mainCamera().getFluidInCamera()) == FogType.NONE,
				"the camera sees water in a dry hold");
			check(!server.computeOnServer(s -> connection.getServerPlayer().isInWater()), "the server has the player in water in a dry hold");
			int air = server.computeOnServer(s -> connection.getServerPlayer().getAirSupply());
			int most = server.computeOnServer(s -> connection.getServerPlayer().getMaxAirSupply());
			check(air == most, "the player lost air in a dry hold: " + air + " of " + most);
			Vec3 feet = context.computeOnClient(client -> client.player.position());
			check(Math.abs(feet.y - (oy + 1)) < 0.01, "the player is not standing on the hold's floor: " + feet);

			// The same place with the hold no longer dry is under water: what the checks above test is real.
			server.runOnServer(s -> PandoricalApi.structures().setDry(ID, List.of()));
			context.waitFor(client -> hold().dry.isEmpty());
			context.waitTicks(10);
			check(context.computeOnClient(client -> client.player.isUnderWater()), "with nothing dry, the client still has the player out of water");
			check(server.computeOnServer(s -> connection.getServerPlayer().isUnderWater()), "with nothing dry, the server still has the player out of water");

			server.runOnServer(s -> PandoricalApi.structures().despawn(ID));
		}
	}

	private static ClientStructure hold() {
		return StructureManager.byId().get(ID);
	}

	private static void check(boolean holds, String otherwise) {
		if (!holds) throw new AssertionError(otherwise);
	}
}
