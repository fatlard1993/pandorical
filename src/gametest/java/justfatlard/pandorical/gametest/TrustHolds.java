package justfatlard.pandorical.gametest;

import com.mojang.authlib.GameProfile;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.Trust;
import justfatlard.pandorical.trust.TrustBook;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Trust holds where the game does each thing: a blow between players, flint and steel on a block,
 * a lava bucket, an end crystal, a strike at somebody else's wolf. Each is tried through the game's
 * own handling, refused, then allowed by a player's own choice, so a hook that stops firing shows up
 * here as the thing it was meant to stop getting through.
 */
public final class TrustHolds implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "pandorical", session -> {
			AtomicReference<String> complaint = new AtomicReference<>();
			// Blows only land on a player who can be hurt; set outright, not by a command a test
			// player may not be allowed to run.
			session.onServer(server -> session.player().setGameMode(GameType.SURVIVAL));
			session.waitTicks(5);

			session.onServer(server -> {
				try {
					pvp(server, session.player());
					fire(server, session.player());
					explosives(server, session.player());
					animals(server, session.player());
				} catch (AssertionError e) {
					complaint.set(e.getMessage());
				}
			});
			check(complaint.get() == null, complaint.get());
		});
	}

	private static void pvp(MinecraftServer server, ServerPlayer player) {
		ServerLevel level = player.level();
		TrustBook book = TrustBook.get(server);
		FakePlayer rival = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "Rival"));
		book.remember(rival.getUUID(), "Rival");

		level.getGameRules().set(GameRules.PVP, true, server);
		check(player.canHarmPlayer(rival), "with nobody set, the gamerule's yes stands");
		check(hit(player, rival), "and a blow lands");

		book.choose(rival.getUUID(), Trust.PVP, false);
		check(!player.canHarmPlayer(rival), "a rival kept out of PvP cannot hurt the player");
		check(!rival.canHarmPlayer(player), "nor be hurt by them: off is off both ways");
		check(!hit(player, rival), "and a blow from them does nothing");

		level.getGameRules().set(GameRules.PVP, false, server);
		book.choose(rival.getUUID(), Trust.PVP, true);
		book.choose(player.getUUID(), Trust.PVP, true);
		check(player.canHarmPlayer(rival), "two players trusted with PvP fight on a PvP-off server");

		book.choose(rival.getUUID(), Trust.PVP, null);
		book.choose(player.getUUID(), Trust.PVP, null);
		check(!player.canHarmPlayer(rival), "and back to the gamerule's no once both go to default");
		level.getGameRules().set(GameRules.PVP, true, server);
	}

	private static void fire(MinecraftServer server, ServerPlayer player) {
		ServerLevel level = player.level();
		TrustBook book = TrustBook.get(server);
		BlockPos ground = player.blockPosition().offset(3, -1, 0);
		level.setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState());
		level.setBlockAndUpdate(ground.above(), Blocks.AIR.defaultBlockState());

		book.chooseDefault(Trust.FIRE, false);
		check(!PandoricalApi.trust().may(player, Trust.FIRE), "fire off by default keeps it from an unset player");
		useOn(player, Items.FLINT_AND_STEEL, ground);
		check(level.getBlockState(ground.above()).isAir(), "flint and steel sets no fire for them");

		BlockPos pour = player.blockPosition().offset(-3, 0, 0);
		level.setBlockAndUpdate(pour.below(), Blocks.STONE.defaultBlockState());
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LAVA_BUCKET));
		player.gameMode.useItem(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND);
		check(player.getMainHandItem().is(Items.LAVA_BUCKET), "nor does a lava bucket empty");

		book.choose(player.getUUID(), Trust.FIRE, true);
		useOn(player, Items.FLINT_AND_STEEL, ground);
		check(level.getBlockState(ground.above()).is(Blocks.FIRE), "trusted by name, the same flint lights it");
		level.setBlockAndUpdate(ground.above(), Blocks.AIR.defaultBlockState());

		book.choose(player.getUUID(), Trust.FIRE, null);
		book.chooseDefault(Trust.FIRE, true);
	}

	private static void explosives(MinecraftServer server, ServerPlayer player) {
		ServerLevel level = player.level();
		TrustBook book = TrustBook.get(server);
		BlockPos base = player.blockPosition().offset(0, -1, 3);
		level.setBlockAndUpdate(base, Blocks.OBSIDIAN.defaultBlockState());
		level.setBlockAndUpdate(base.above(), Blocks.AIR.defaultBlockState());
		level.setBlockAndUpdate(base.above(2), Blocks.AIR.defaultBlockState());

		book.choose(player.getUUID(), Trust.EXPLOSIVES, false);
		useOn(player, Items.END_CRYSTAL, base);
		check(level.getEntitiesOfClass(net.minecraft.world.entity.boss.enderdragon.EndCrystal.class,
			new net.minecraft.world.phys.AABB(base).inflate(2)).isEmpty(), "no end crystal goes down for them");
		book.choose(player.getUUID(), Trust.EXPLOSIVES, null);
	}

	private static void animals(MinecraftServer server, ServerPlayer player) {
		ServerLevel level = player.level();
		TrustBook book = TrustBook.get(server);
		FakePlayer owner = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "Owner"));
		Wolf wolf = EntityTypes.WOLF.create(level, EntitySpawnReason.COMMAND);
		wolf.snapTo(player.getX() + 2, player.getY(), player.getZ(), 0, 0);
		wolf.tame(owner);
		level.addFreshEntity(wolf);

		book.choose(player.getUUID(), Trust.ANIMALS, false);
		float before = wolf.getHealth();
		wolf.hurtServer(level, level.damageSources().playerAttack(player), 2);
		check(wolf.getHealth() == before, "somebody else's wolf is not theirs to hurt");
		check(!PandoricalApi.trust().mayHurt(player, wolf), "and mods are told the same");

		book.choose(player.getUUID(), Trust.ANIMALS, null);
		wolf.setInvulnerableTime(0);
		wolf.hurtServer(level, level.damageSources().playerAttack(player), 2);
		check(wolf.getHealth() < before, "by default they can, as the game always let them");
		wolf.discard();
	}

	/** One blow from {@code attacker} to {@code victim} through the game's damage; whether it landed. */
	private static boolean hit(ServerPlayer victim, ServerPlayer attacker) {
		victim.setInvulnerableTime(0);
		victim.setHealth(victim.getMaxHealth());
		victim.hurtServer(victim.level(), victim.level().damageSources().playerAttack(attacker), 2);
		return victim.getHealth() < victim.getMaxHealth();
	}

	private static void useOn(ServerPlayer player, net.minecraft.world.item.Item item, BlockPos pos) {
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
		player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
	}

	private static void check(boolean ok, String complaint) {
		if (!ok) throw new AssertionError(complaint);
	}
}
