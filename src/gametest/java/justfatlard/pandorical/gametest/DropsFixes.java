package justfatlard.pandorical.gametest;

import justfatlard.pandorical.drops.DropsPolicy;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.List;

/** The drops fixes do what DropsApi says with each on, and nothing with each off. */
public final class DropsFixes implements FabricClientGameTest {
	/** One of each vanilla orb size, repeated. */
	private static final int[] ORB_VALUES = {1, 3, 7, 17, 37, 73, 149, 307, 617, 1237, 2477};
	private static final int ORBS = 200;
	private static final int MOST_ORB_VALUE = Short.MAX_VALUE;
	private static final int PICK_DAMAGE = 1001;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();

			BlockPos spawn = server.computeOnServer(s -> connection.getServerPlayer().blockPosition());
			BlockPos pit = spawn.east(12);
			server.runOnServer(s -> {
				for (Direction side : Direction.Plane.HORIZONTAL) {
					s.overworld().setBlockAndUpdate(pit.relative(side), Blocks.STONE.defaultBlockState());
					s.overworld().setBlockAndUpdate(pit.relative(side).above(), Blocks.STONE.defaultBlockState());
				}
			});

			orbsOfTwoValuesStayApartWhenOff(context, server, pit);
			orbsClumpAndOneTouchTakesTheLot(context, server, connection, pit);
			server.runCommand("tp @a " + spawn.getX() + " " + spawn.getY() + " " + spawn.getZ());

			BlockPos floor = spawn.west(12);
			check(stacksAfterAWhile(context, server, floor, false) == 2, "with the reach at vanilla's, stacks 1.5 apart merged");
			server.runOnServer(s -> DropsPolicy.chooseMergeRadius(s, 20));
			check(stacksAfterAWhile(context, server, floor, false) == 1, "with a 2-block reach, stacks 1.5 apart stayed apart");
			check(stacksAfterAWhile(context, server, floor, true) == 2, "with a 2-block reach, stacks merged through glass");
			server.runOnServer(s -> DropsPolicy.chooseMergeRadius(s, DropsPolicy.VANILLA_MERGE_TENTHS));

			BlockPos far = spawn.east(24).above();
			server.runOnServer(s -> DropsPolicy.chooseTrackingRange(s, 16));
			int unseen = dropAt(server, far);
			context.waitTicks(20);
			server.runOnServer(s -> DropsPolicy.chooseTrackingRange(s, 0));
			int seen = dropAt(server, far);
			context.waitFor(client -> client.level.getEntity(seen) != null);
			check(context.computeOnClient(client -> client.level.getEntity(unseen) == null),
				"an item 24 blocks off reached the client with the range at 16");
		}
	}

	private static int dropAt(TestServerContext server, BlockPos pos) {
		return server.computeOnServer(s -> {
			ItemEntity stack = new ItemEntity(s.overworld(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, new ItemStack(Items.STICK));
			stack.setDeltaMovement(Vec3.ZERO);
			s.overworld().addFreshEntity(stack);
			return stack.getId();
		});
	}

	/**
	 * Two stacks of cobblestone 1.5 blocks apart on the ground, with glass between them if asked,
	 * and how many stacks there are a while later. Each box clears the glass's block.
	 */
	private static int stacksAfterAWhile(ClientGameTestContext context, TestServerContext server, BlockPos floor, boolean glass) {
		BlockPos between = floor.east();
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			if (glass) level.setBlockAndUpdate(between, Blocks.GLASS.defaultBlockState());
			for (double x : new double[] {0.75, 2.25}) {
				ItemEntity stack = new ItemEntity(level, floor.getX() + x, floor.getY() + 0.05, floor.getZ() + 0.5, new ItemStack(Items.COBBLESTONE));
				stack.setDeltaMovement(Vec3.ZERO);
				level.addFreshEntity(stack);
			}
		});
		context.waitTicks(100);
		return server.computeOnServer(s -> {
			List<ItemEntity> stacks = s.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(between).inflate(3));
			int left = stacks.size();
			int items = stacks.stream().mapToInt(stack -> stack.getItem().getCount()).sum();
			check(items == 2, "2 cobblestone became " + items);
			stacks.forEach(Entity::discard);
			s.overworld().setBlockAndUpdate(between, Blocks.AIR.defaultBlockState());
			return left;
		});
	}

	private static void orbsOfTwoValuesStayApartWhenOff(ClientGameTestContext context, TestServerContext server, BlockPos pit) {
		server.runOnServer(s -> {
			spawnOrb(s.overworld(), pit, 1);
			spawnOrb(s.overworld(), pit, 3);
		});
		context.waitTicks(45);
		server.runOnServer(s -> {
			List<ExperienceOrb> orbs = orbsAt(s, pit);
			check(orbs.size() == 2, "with clumping off, orbs of 1 and 3 merged: " + orbs.size() + " left");
			orbs.forEach(Entity::discard);
		});
	}

	private static void orbsClumpAndOneTouchTakesTheLot(ClientGameTestContext context, TestServerContext server,
			TestServerConnection connection, BlockPos pit) {
		int total = server.computeOnServer(s -> {
			DropsPolicy.chooseClumping(s, true);
			int sum = 0;
			for (int i = 0; i < ORBS; i++) {
				int value = ORB_VALUES[i % ORB_VALUES.length];
				spawnOrb(s.overworld(), pit, value);
				sum += value;
			}
			return sum;
		});
		int fewest = (total + MOST_ORB_VALUE - 1) / MOST_ORB_VALUE;
		server.waitFor(s -> orbsAt(s, pit).size() <= fewest + 2, 200);
		context.waitTicks(25);
		server.runOnServer(s -> {
			List<ExperienceOrb> orbs = orbsAt(s, pit);
			long sum = 0;
			for (ExperienceOrb orb : orbs) {
				long each = (long) orb.getValue() * count(orb);
				check(each <= MOST_ORB_VALUE, "a clump holds " + each + ", more than Value saves");
				check(count(orb) == 1, "a clump has count " + count(orb));
				sum += each;
			}
			check(sum == total, "clumping changed the XP lying about from " + total + " to " + sum);
			check(orbs.size() <= fewest + 2, ORBS + " orbs came to " + orbs.size() + ", not about " + fewest);
		});

		// One damaged Mending pick: vanilla repairs all of it, and a repair of 2n points costs n XP, rounded down.
		int before = server.computeOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
			pick.enchant(s.overworld().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING), 1);
			pick.setDamageValue(PICK_DAMAGE);
			player.setItemSlot(EquipmentSlot.MAINHAND, pick);
			return player.totalExperience;
		});
		server.runCommand("tp @a " + (pit.getX() + 0.5) + " " + pit.getY() + " " + (pit.getZ() + 0.5));
		server.waitFor(s -> orbsAt(s, pit).isEmpty(), 200);
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			int gained = player.totalExperience - before;
			int damage = player.getItemBySlot(EquipmentSlot.MAINHAND).getDamageValue();
			check(damage == 0, "Mending left the pick at " + damage + " damage");
			check(gained == total - PICK_DAMAGE / 2, "the player gained " + gained + " XP from " + total + ", not " + (total - PICK_DAMAGE / 2));
			player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			DropsPolicy.chooseClumping(s, false);
		});
	}

	private static void spawnOrb(ServerLevel level, BlockPos pit, int value) {
		ExperienceOrb orb = new ExperienceOrb(level, pit.getX() + 0.5, pit.getY() + 0.1, pit.getZ() + 0.5, value);
		orb.setDeltaMovement(Vec3.ZERO);
		level.addFreshEntity(orb);
	}

	private static List<ExperienceOrb> orbsAt(MinecraftServer server, BlockPos pit) {
		return server.overworld().getEntitiesOfClass(ExperienceOrb.class, new AABB(pit).inflate(2));
	}

	private static int count(ExperienceOrb orb) {
		try {
			Field count = ExperienceOrb.class.getDeclaredField("count");
			count.setAccessible(true);
			return count.getInt(orb);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("reading an orb's count", e);
		}
	}

	private static void check(boolean holds, String otherwise) {
		if (!holds) throw new AssertionError(otherwise);
	}
}
