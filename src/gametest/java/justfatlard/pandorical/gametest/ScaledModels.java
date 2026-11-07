package justfatlard.pandorical.gametest;

import justfatlard.pandorical.api.BlockEntry;
import justfatlard.pandorical.api.Capabilities;
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
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A structure drawn at a scale, and a block with a shape of its own where it stands, as a client
 * takes them, each check paired with the way it could go wrong: the scale refused when it is no
 * scale at all and reset by a spawn; a shape refused with no boxes, never given to air, gone when
 * it was cleared while the player was in another world, and sent to no client that did not
 * declare it. A block mark taken off while the player is away is gone on their return too.
 */
public final class ScaledModels implements FabricClientGameTest {
	private static final String ID = "pandorical-gametest:model";
	private static final float QUARTER = 0.25F;
	private static final String MARK = "pandorical-gametest:mark";

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!Smoke.full()) return;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			server.waitFor(s -> PandoricalApi.isAvailable(connection.getServerPlayer()));
			BlockPos spawn = server.computeOnServer(s -> connection.getServerPlayer().blockPosition());
			BlockPos at = spawn.east(3);

			scale(context, server, at.north(3));
			shapes(context, server, connection, at);
			unasked(context, server, connection, at.north(3), at.south(3));
		}
	}

	private static void scale(ClientGameTestContext context, TestServerContext server, BlockPos at) {
		server.runOnServer(s -> {
			ArmorStand anchor = new ArmorStand(EntityTypes.ARMOR_STAND, s.overworld());
			anchor.setPos(at.getX(), at.getY(), at.getZ());
			anchor.setNoGravity(true);
			anchor.setInvisible(true);
			s.overworld().addFreshEntity(anchor);
			PandoricalApi.structures().spawn(anchor, ID, List.of(new BlockEntry(new RelPos(0, 0, 0), Blocks.STONE.defaultBlockState())),
				new StructurePose(at.getX(), at.getY(), at.getZ(), 0));
		});
		for (float bad : new float[] {0F, -1F, Float.NaN, Float.POSITIVE_INFINITY}) {
			boolean refused = server.computeOnServer(s -> {
				try {
					PandoricalApi.structures().setScale(ID, bad);
					return false;
				} catch (IllegalArgumentException expected) {
					return true;
				}
			});
			check(refused, "a scale of " + bad + " was taken");
		}

		server.runOnServer(s -> PandoricalApi.structures().setScale(ID, QUARTER));
		context.waitFor(client -> model() != null && model().scale == QUARTER);

		// A spawn starts it afresh at full size, on the client too: the scale has to be set again.
		server.runOnServer(s -> {
			ArmorStand anchor = s.overworld().getEntitiesOfClass(ArmorStand.class, new AABB(at).inflate(1)).get(0);
			PandoricalApi.structures().spawn(anchor, ID, List.of(new BlockEntry(new RelPos(0, 0, 0), Blocks.STONE.defaultBlockState())),
				new StructurePose(at.getX(), at.getY(), at.getZ(), 0));
		});
		context.waitFor(client -> model() != null && model().scale == 1.0F);
		server.runOnServer(s -> PandoricalApi.structures().setScale(ID, QUARTER));
		context.waitFor(client -> model().scale == QUARTER);
	}

	private static void shapes(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, BlockPos at) {
		boolean refused = server.computeOnServer(s -> {
			try {
				PandoricalApi.blockShapes().setShape(s.overworld(), at, List.of());
				return false;
			} catch (IllegalArgumentException expected) {
				return true;
			}
		});
		check(refused, "a shape with no boxes was taken");

		server.runOnServer(s -> {
			s.overworld().setBlockAndUpdate(at, Blocks.GLASS.defaultBlockState());
			PandoricalApi.blockShapes().setShape(s.overworld(), at, List.of(new AABB(0, 0, 0, 1, 0.5, 1)));
		});
		context.waitFor(client -> top(client, at) == 0.5);

		// Air is never given the shape, though the shape is still set: a block gone is not solid.
		server.runOnServer(s -> s.overworld().setBlockAndUpdate(at, Blocks.AIR.defaultBlockState()));
		context.waitFor(client -> client.level.getBlockState(at).isAir());
		check(context.computeOnClient(client -> client.level.getBlockState(at).getCollisionShape(client.level, at).isEmpty()),
			"air took the shape set where a block had been");
		server.runOnServer(s -> s.overworld().setBlockAndUpdate(at, Blocks.GLASS.defaultBlockState()));
		context.waitFor(client -> top(client, at) == 0.5);

		// Cleared while the player is in another world, it is gone when they come back; a mark too.
		server.runOnServer(s -> PandoricalApi.blockMarks().mark(s.overworld(), at, MARK));
		context.waitFor(client -> justfatlard.pandorical.client.renderer.ClientBlockMarks.has(at, MARK));
		BlockPos home = server.computeOnServer(s -> connection.getServerPlayer().blockPosition());
		server.runCommand("execute in minecraft:the_nether run tp @a " + home.getX() + " 100 " + home.getZ());
		context.waitFor(client -> client.level.dimension().equals(net.minecraft.world.level.Level.NETHER));
		server.runOnServer(s -> {
			PandoricalApi.blockShapes().clearShape(s.overworld(), at);
			PandoricalApi.blockMarks().unmark(s.overworld(), at, MARK);
		});
		server.runCommand("execute in minecraft:overworld run tp @a " + home.getX() + " " + home.getY() + " " + home.getZ());
		context.waitFor(client -> client.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD));
		connection.waitForChunksRender();
		context.waitTicks(10);
		check(context.computeOnClient(client -> top(client, at)) == 1.0,
			"a shape cleared while the player was away came back with them: " + context.computeOnClient(client -> top(client, at)));
		check(!context.computeOnClient(client -> justfatlard.pandorical.client.renderer.ClientBlockMarks.has(at, MARK)),
			"a mark taken off while the player was away came back with them");
	}

	/** A client that did not declare the two capabilities is sent neither: the scale and the shape are left alone. */
	private static void unasked(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, BlockPos model, BlockPos block) {
		UUID player = server.computeOnServer(s -> connection.getServerPlayer().getUUID());
		Set<String> without = new HashSet<>(Capabilities.CLIENT);
		without.remove(Capabilities.SCALED_STRUCTURES);
		without.remove(Capabilities.BLOCK_SHAPES);
		server.runOnServer(s -> PandoricalApi.registerPlayerCapabilities(player, without));
		try {
			server.runOnServer(s -> {
				s.overworld().setBlockAndUpdate(block, Blocks.GLASS.defaultBlockState());
				PandoricalApi.blockShapes().setShape(s.overworld(), block, List.of(new AABB(0, 0, 0, 1, 0.5, 1)));
				PandoricalApi.structures().setScale(ID, 0.5F);
			});
			context.waitTicks(10);
			check(context.computeOnClient(client -> model().scale) == QUARTER, "a client without scaled_structures was sent a scale");
			check(context.computeOnClient(client -> top(client, block)) == 1.0, "a client without block_shapes was sent a shape");
		} finally {
			server.runOnServer(s -> PandoricalApi.registerPlayerCapabilities(player, new HashSet<>(Capabilities.CLIENT)));
			server.runOnServer(s -> {
				PandoricalApi.blockShapes().clearShape(s.overworld(), block);
				PandoricalApi.structures().despawn(ID);
			});
		}
	}

	private static double top(net.minecraft.client.Minecraft client, BlockPos at) {
		return client.level.getBlockState(at).getCollisionShape(client.level, at).max(Direction.Axis.Y);
	}

	private static ClientStructure model() {
		return StructureManager.byId().get(ID);
	}

	private static void check(boolean holds, String otherwise) {
		if (!holds) throw new AssertionError(otherwise);
	}
}
