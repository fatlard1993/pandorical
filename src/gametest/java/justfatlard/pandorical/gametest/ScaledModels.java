package justfatlard.pandorical.gametest;

import justfatlard.pandorical.api.BlockEntry;
import justfatlard.pandorical.api.Capabilities;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.RelPos;
import justfatlard.pandorical.api.StructurePose;
import justfatlard.pandorical.client.renderer.ClientBlockMaterials;
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
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
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
 * declare it. A block mark taken off while the player is away is gone on their return too. A
 * material is refused as air, heard at its position and nowhere else, kept through the block's
 * own removal until the server clears it, gone when cleared while the player was away, and sent
 * to no client that did not declare it. A block carrying a drawing reaches a client as its build,
 * culled and turned once as it arrives, is redrawn when sent again and plain when replaced without
 * one, keeps its drawing through a change of state, is found through a registered provider, and
 * reaches a client without {@code structure_cells} as the plain block.
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
			materials(context, server, connection, at.east(3));
			drawings(context, server, connection, at.west(3));
		}
	}

	private static final String DRAWN = "pandorical-gametest:drawn";
	private static final String DRAWING_MARK = "pandorical_gametest_drawing";

	/** A 2-cube with one stone cell at its first corner, turned and lifted as asked. */
	private static justfatlard.pandorical.api.CellsDrawing corner(int turns, float lift) {
		byte[] cells = new byte[8];
		cells[0] = 1;
		return new justfatlard.pandorical.api.CellsDrawing(2, 2, List.of(Blocks.STONE.defaultBlockState()), cells, lift, turns);
	}

	private static void drawings(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, BlockPos at) {
		boolean refused = server.computeOnServer(s -> {
			try {
				new justfatlard.pandorical.api.CellsDrawing(2, 2, List.of(), new byte[7], 0, 0);
				return false;
			} catch (IllegalArgumentException expected) {
				return true;
			}
		});
		check(refused, "a drawing whose cells do not add up was taken");

		// Found through a provider, by the saved data and not the state alone.
		server.runOnServer(s -> PandoricalApi.structures().drawings().register((state, data) ->
			data != null && data.contains(DRAWING_MARK) ? java.util.Optional.of(corner(1, 0.5F)) : java.util.Optional.empty()));
		net.minecraft.nbt.CompoundTag marked = new net.minecraft.nbt.CompoundTag();
		marked.putBoolean(DRAWING_MARK, true);
		check(server.computeOnServer(s -> PandoricalApi.structures().drawings().entry(new RelPos(0, 0, 0), Blocks.BARREL.defaultBlockState(), null).drawing()) == null,
			"a block with no saved data was given a drawing");
		check(corner(1, 0.5F).equals(server.computeOnServer(s -> PandoricalApi.structures().drawings().entry(new RelPos(0, 0, 0), Blocks.BARREL.defaultBlockState(), marked).drawing())),
			"the provider's drawing was not attached to the entry");

		server.runOnServer(s -> {
			ArmorStand anchor = new ArmorStand(EntityTypes.ARMOR_STAND, s.overworld());
			anchor.setPos(at.getX(), at.getY(), at.getZ());
			anchor.setNoGravity(true);
			anchor.setInvisible(true);
			s.overworld().addFreshEntity(anchor);
			PandoricalApi.structures().spawn(anchor, DRAWN, List.of(
					PandoricalApi.structures().drawings().entry(new RelPos(0, 1, 0), Blocks.BARREL.defaultBlockState(), marked),
					new BlockEntry(new RelPos(1, 1, 0), Blocks.OAK_PLANKS.defaultBlockState())),
				new StructurePose(at.getX(), at.getY(), at.getZ(), 0));
		});
		context.waitFor(client -> drawn() != null && drawn().drawingAt(0, 1, 0) != null);
		var first = context.computeOnClient(client -> drawn().drawingAt(0, 1, 0));
		check(first.n() == 2 && first.lift() == 0.5F && first.showing().size() == 1, "the drawing arrived as " + first);
		var cell = first.showing().get(0);
		// One quarter turn clockwise takes the cell at (0, 0) to (n - 1, 0).
		check(cell.x() == 1 && cell.y() == 0 && cell.z() == 0 && cell.state().is(Blocks.STONE), "the drawing's cell was not turned: " + cell);
		check(context.computeOnClient(client -> drawn().drawingAt(1, 1, 0)) == null, "a plain block was drawn as a build");
		check(context.computeOnClient(client -> drawn().blocks.get(new StructureManager.RelPosKey(0, 1, 0))).is(Blocks.BARREL),
			"the block under a drawing is not still the block");

		// Sent again, redrawn; a change of state keeps it; replaced without one, plain; removed, gone.
		server.runOnServer(s -> PandoricalApi.structures().updateBlocks(DRAWN,
			List.of(new BlockEntry(new RelPos(0, 1, 0), Blocks.BARREL.defaultBlockState(), corner(0, 0F))), List.of(), java.util.Map.of()));
		context.waitFor(client -> drawn().drawingAt(0, 1, 0) != null && drawn().drawingAt(0, 1, 0).lift() == 0F);
		check(context.computeOnClient(client -> drawn().drawingAt(0, 1, 0).showing().get(0).x()) == 0, "an unturned drawing was turned");
		server.runOnServer(s -> PandoricalApi.structures().updateBlocks(DRAWN, List.of(), List.of(),
			java.util.Map.of(new RelPos(0, 1, 0), Blocks.BARREL.defaultBlockState().setValue(net.minecraft.world.level.block.BarrelBlock.OPEN, true))));
		context.waitFor(client -> drawn().blocks.get(new StructureManager.RelPosKey(0, 1, 0)).getValue(net.minecraft.world.level.block.BarrelBlock.OPEN));
		check(context.computeOnClient(client -> drawn().drawingAt(0, 1, 0)) != null, "a change of state dropped the drawing");
		server.runOnServer(s -> PandoricalApi.structures().updateBlocks(DRAWN,
			List.of(new BlockEntry(new RelPos(0, 1, 0), Blocks.BARREL.defaultBlockState())), List.of(), java.util.Map.of()));
		context.waitFor(client -> drawn().drawingAt(0, 1, 0) == null);
		server.runOnServer(s -> PandoricalApi.structures().updateBlocks(DRAWN,
			List.of(new BlockEntry(new RelPos(2, 1, 0), Blocks.BARREL.defaultBlockState(), corner(2, 0F))), List.of(), java.util.Map.of()));
		context.waitFor(client -> drawn().drawingAt(2, 1, 0) != null);
		server.runOnServer(s -> PandoricalApi.structures().updateBlocks(DRAWN, List.of(), List.of(new RelPos(2, 1, 0)), java.util.Map.of()));
		context.waitFor(client -> drawn().drawingAt(2, 1, 0) == null && drawn().drawingCount() == 0);

		// A client without structure_cells is sent the structure and not the drawings.
		UUID player = server.computeOnServer(s -> connection.getServerPlayer().getUUID());
		Set<String> without = new HashSet<>(Capabilities.CLIENT);
		without.remove(Capabilities.STRUCTURE_CELLS);
		server.runOnServer(s -> PandoricalApi.registerPlayerCapabilities(player, without));
		try {
			server.runOnServer(s -> {
				ArmorStand anchor = s.overworld().getEntitiesOfClass(ArmorStand.class, new AABB(at).inflate(1)).get(0);
				PandoricalApi.structures().spawn(anchor, DRAWN, List.of(
						new BlockEntry(new RelPos(0, 1, 0), Blocks.BARREL.defaultBlockState(), corner(0, 0F)),
						new BlockEntry(new RelPos(1, 1, 0), Blocks.OAK_PLANKS.defaultBlockState())),
					new StructurePose(at.getX(), at.getY(), at.getZ(), 0));
				PandoricalApi.structures().updateBlocks(DRAWN,
					List.of(new BlockEntry(new RelPos(1, 1, 0), Blocks.BARREL.defaultBlockState(), corner(0, 0F))), List.of(), java.util.Map.of());
			});
			context.waitFor(client -> drawn() != null && drawn().blocks.get(new StructureManager.RelPosKey(1, 1, 0)).is(Blocks.BARREL));
			context.waitTicks(10);
			check(context.computeOnClient(client -> drawn().drawingCount()) == 0, "a client without structure_cells was sent a drawing");
			check(context.computeOnClient(client -> drawn().blocks.get(new StructureManager.RelPosKey(0, 1, 0))).is(Blocks.BARREL),
				"a client without structure_cells did not get the plain block");
		} finally {
			server.runOnServer(s -> PandoricalApi.registerPlayerCapabilities(player, new HashSet<>(Capabilities.CLIENT)));
			server.runOnServer(s -> PandoricalApi.structures().despawn(DRAWN));
		}
	}

	private static ClientStructure drawn() {
		return StructureManager.byId().get(DRAWN);
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

	private static void materials(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, BlockPos at) {
		boolean refused = server.computeOnServer(s -> {
			try {
				PandoricalApi.blockMaterials().setMaterial(s.overworld(), at, Blocks.AIR.defaultBlockState());
				return false;
			} catch (IllegalArgumentException expected) {
				return true;
			}
		});
		check(refused, "air was taken as a material");

		BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
		server.runOnServer(s -> {
			s.overworld().setBlockAndUpdate(at, Blocks.GLASS.defaultBlockState());
			s.overworld().setBlockAndUpdate(at.north(), Blocks.GLASS.defaultBlockState());
			PandoricalApi.blockMaterials().setMaterial(s.overworld(), at, planks);
		});
		context.waitFor(client -> ClientBlockMaterials.at(at) == planks && client.level.getBlockState(at.north()).is(Blocks.GLASS));

		// What a step, a hit and a break ask for the sound: the material's here, the block's own next door.
		check(context.computeOnClient(client -> ClientBlockMaterials.soundType(client.level.getBlockState(at), at)) == SoundType.WOOD,
			"glass given planks as its material does not sound like wood");
		check(context.computeOnClient(client -> ClientBlockMaterials.soundType(client.level.getBlockState(at.north()), at.north())) == SoundType.GLASS,
			"glass with no material set does not sound like glass");
		check(context.computeOnClient(client -> ClientBlockMaterials.as(Blocks.AIR.defaultBlockState(), at)).isAir(),
			"air took the material set where a block had been");

		// The block goes first and the clear after, as when a player breaks it: the material waits for the clear.
		server.runOnServer(s -> s.overworld().setBlockAndUpdate(at, Blocks.AIR.defaultBlockState()));
		context.waitFor(client -> client.level.getBlockState(at).isAir());
		check(context.computeOnClient(client -> ClientBlockMaterials.at(at)) == planks, "the material went with the block, before the server cleared it");
		server.runOnServer(s -> s.overworld().setBlockAndUpdate(at, Blocks.GLASS.defaultBlockState()));

		// Cleared while the player is in another world, it is gone when they come back; one still set comes back with them.
		server.runOnServer(s -> PandoricalApi.blockMaterials().setMaterial(s.overworld(), at.north(), planks));
		context.waitFor(client -> ClientBlockMaterials.at(at.north()) == planks);
		BlockPos home = server.computeOnServer(s -> connection.getServerPlayer().blockPosition());
		server.runCommand("execute in minecraft:the_nether run tp @a " + home.getX() + " 100 " + home.getZ());
		context.waitFor(client -> client.level.dimension().equals(net.minecraft.world.level.Level.NETHER));
		check(context.computeOnClient(client -> ClientBlockMaterials.at(at)) == null, "the overworld's material is heard in the nether");
		server.runOnServer(s -> PandoricalApi.blockMaterials().clearMaterial(s.overworld(), at));
		server.runCommand("execute in minecraft:overworld run tp @a " + home.getX() + " " + home.getY() + " " + home.getZ());
		context.waitFor(client -> client.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD));
		connection.waitForChunksRender();
		context.waitTicks(10);
		check(context.computeOnClient(client -> ClientBlockMaterials.at(at.north())) == planks, "a material still set did not come back with the player");
		check(context.computeOnClient(client -> ClientBlockMaterials.at(at)) == null, "a material cleared while the player was away came back with them");
		server.runOnServer(s -> PandoricalApi.blockMaterials().clearMaterial(s.overworld(), at.north()));
		context.waitFor(client -> ClientBlockMaterials.at(at.north()) == null);
	}

	/** A client that did not declare the capabilities is sent none of them: the scale, the shape and the material are left alone. */
	private static void unasked(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, BlockPos model, BlockPos block) {
		UUID player = server.computeOnServer(s -> connection.getServerPlayer().getUUID());
		Set<String> without = new HashSet<>(Capabilities.CLIENT);
		without.remove(Capabilities.SCALED_STRUCTURES);
		without.remove(Capabilities.BLOCK_SHAPES);
		without.remove(Capabilities.BLOCK_MATERIALS);
		server.runOnServer(s -> PandoricalApi.registerPlayerCapabilities(player, without));
		try {
			server.runOnServer(s -> {
				s.overworld().setBlockAndUpdate(block, Blocks.GLASS.defaultBlockState());
				PandoricalApi.blockShapes().setShape(s.overworld(), block, List.of(new AABB(0, 0, 0, 1, 0.5, 1)));
				PandoricalApi.structures().setScale(ID, 0.5F);
				PandoricalApi.blockMaterials().setMaterial(s.overworld(), block, Blocks.OAK_PLANKS.defaultBlockState());
			});
			context.waitTicks(10);
			check(context.computeOnClient(client -> model().scale) == QUARTER, "a client without scaled_structures was sent a scale");
			check(context.computeOnClient(client -> top(client, block)) == 1.0, "a client without block_shapes was sent a shape");
			check(context.computeOnClient(client -> ClientBlockMaterials.at(block)) == null, "a client without block_materials was sent a material");
		} finally {
			server.runOnServer(s -> PandoricalApi.registerPlayerCapabilities(player, new HashSet<>(Capabilities.CLIENT)));
			server.runOnServer(s -> {
				PandoricalApi.blockShapes().clearShape(s.overworld(), block);
				PandoricalApi.blockMaterials().clearMaterial(s.overworld(), block);
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
