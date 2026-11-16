package justfatlard.pandorical.gametest;

import justfatlard.pandorical.api.BlockEntry;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.RelPos;
import justfatlard.pandorical.api.StructurePose;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A walkable structure is solid on the server to what is not a player, turned or not: an item and
 * an armour stand dropped on it come to rest on its blocks, on a slab at the slab's height, and a
 * structure that is not walkable, or is hidden, lets them fall through.
 */
public final class SolidDecks implements FabricClientGameTest {
	private static final String ID = "pandorical-gametest:solid";
	/** The deck's height above the player, so nothing below it is in the way of a fall. */
	private static final int HIGH = 30;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!Smoke.full()) return;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			server.waitFor(s -> PandoricalApi.isAvailable(connection.getServerPlayer()));

			BlockPos spawn = server.computeOnServer(s -> connection.getServerPlayer().blockPosition());
			Vec3 origin = new Vec3(spawn.getX() + 0.5, spawn.getY() + HIGH, spawn.getZ() + 0.5);

			// Turned off a quarter, so the boxes are worked out at a heading, not on the grid.
			float yaw = 30.0f;
			server.runOnServer(s -> {
				ArmorStand anchor = new ArmorStand(EntityTypes.ARMOR_STAND, s.overworld());
				anchor.setPos(origin);
				anchor.setNoGravity(true);
				anchor.setInvisible(true);
				s.overworld().addFreshEntity(anchor);

				List<BlockEntry> deck = new ArrayList<>();
				for (int x = -4; x <= 4; x++) {
					for (int z = -4; z <= 4; z++) {
						deck.add(new BlockEntry(new RelPos(x, 0, z), x == 3 && z == 0
							? Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM)
							: Blocks.OAK_PLANKS.defaultBlockState()));
					}
				}
				PandoricalApi.structures().spawn(anchor, ID, deck, new StructurePose(origin.x, origin.y, origin.z, yaw));
				PandoricalApi.structures().setWalkable(ID, true);
			});

			int item = drop(server, onDeck(origin, yaw, -1.5, 1.5, 3), true);
			int stand = drop(server, onDeck(origin, yaw, 1.5, -1.5, 3), false);
			int onSlab = drop(server, onDeck(origin, yaw, 3.5, 0.5, 3), true);
			context.waitTicks(60);
			double top = origin.y + 1;
			checkResting(server, item, top, "an item dropped on the deck");
			checkResting(server, stand, top, "an armour stand dropped on the deck");
			checkResting(server, onSlab, origin.y + 0.5, "an item dropped on the slab");

			// Not walkable: nothing holds them up.
			server.runOnServer(s -> PandoricalApi.structures().setWalkable(ID, false));
			int through = drop(server, onDeck(origin, yaw, 0.5, 0.5, 3), true);
			context.waitTicks(40);
			checkBelow(server, through, origin.y - 2, "an item on a deck that is not walkable");

			// Walkable but hidden: the same.
			server.runOnServer(s -> {
				PandoricalApi.structures().setWalkable(ID, true);
				PandoricalApi.structures().setVisible(ID, false);
			});
			int hidden = drop(server, onDeck(origin, yaw, -0.5, -0.5, 3), true);
			context.waitTicks(40);
			checkBelow(server, hidden, origin.y - 2, "an item on a hidden deck");

			server.runOnServer(s -> PandoricalApi.structures().despawn(ID));
		}
	}

	/** A point above the deck, in its own frame, in the world. */
	private static Vec3 onDeck(Vec3 origin, float yaw, double lx, double lz, double up) {
		double r = Math.toRadians(yaw);
		return new Vec3(origin.x + lx * Math.cos(r) - lz * Math.sin(r), origin.y + up,
			origin.z + lx * Math.sin(r) + lz * Math.cos(r));
	}

	private static int drop(TestServerContext server, Vec3 at, boolean item) {
		return server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			Entity dropped;
			if (item) {
				ItemEntity entity = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.STICK));
				entity.setDeltaMovement(Vec3.ZERO);
				entity.setNeverPickUp();
				entity.setUnlimitedLifetime();
				dropped = entity;
			} else {
				ArmorStand entity = new ArmorStand(EntityTypes.ARMOR_STAND, level);
				entity.setPos(at);
				dropped = entity;
			}
			level.addFreshEntity(dropped);
			return dropped.getId();
		});
	}

	private static void checkResting(TestServerContext server, int id, double expected, String what) {
		double y = server.computeOnServer(s -> s.overworld().getEntity(id).getY());
		check(Math.abs(y - expected) < 0.05, what + " is at y " + y + ", not resting at " + expected);
	}

	private static void checkBelow(TestServerContext server, int id, double below, String what) {
		double y = server.computeOnServer(s -> {
			Entity entity = s.overworld().getEntity(id);
			return entity == null ? Double.NEGATIVE_INFINITY : entity.getY();
		});
		check(y < below, what + " is held up at y " + y);
	}

	private static void check(boolean holds, String otherwise) {
		if (!holds) throw new AssertionError(otherwise);
	}
}
