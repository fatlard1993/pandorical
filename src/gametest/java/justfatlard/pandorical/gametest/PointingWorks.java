package justfatlard.pandorical.gametest;

import justfatlard.pandorical.point.Pointing;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.UUID;

/**
 * Pointing finds what the player looks at, a mob before the block behind it; is drawn in the
 * player's colour, white with no team and the team's once they join one; lasts its few seconds;
 * and will not repeat on a second press straight after the first.
 */
public final class PointingWorks implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (!Smoke.full()) return;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			BlockPos feet = server.computeOnServer(s -> connection.getServerPlayer().blockPosition());
			BlockPos target = feet.east(6).above();
			server.runOnServer(s -> s.overworld().setBlockAndUpdate(target, Blocks.GOLD_BLOCK.defaultBlockState()));
			server.runCommand("tp @a " + (feet.getX() + 0.5) + " " + feet.getY() + " " + (feet.getZ() + 0.5)
				// Facing is aimed from the feet, and pointing traces from the eyes: aim an eye's height
				// below the block's middle, so the line from the feet runs parallel to the one from the eyes.
				+ " facing " + (target.getX() + 0.5) + " " + (target.getY() + 0.5 - 1.62) + " " + (target.getZ() + 0.5));
			context.waitTicks(5);

			HitResult hit = server.computeOnServer(s -> Pointing.trace(connection.getServerPlayer()));
			check(hit instanceof BlockHitResult block && block.getBlockPos().equals(target), "pointing did not find the gold block: " + hit);

			server.runOnServer(s -> {
				ArmorStand stand = new ArmorStand(EntityTypes.ARMOR_STAND, s.overworld());
				stand.setPos(feet.getX() + 3.5, feet.getY(), feet.getZ() + 0.5);
				s.overworld().addFreshEntity(stand);
			});
			context.waitTicks(2);
			hit = server.computeOnServer(s -> Pointing.trace(connection.getServerPlayer()));
			check(hit instanceof EntityHitResult entity && entity.getEntity() instanceof ArmorStand, "pointing did not take the mob in front: " + hit);

			check(server.computeOnServer(s -> Pointing.colorOf(connection.getServerPlayer())) == 0xFFFFFF, "a player with no team is not white");
			server.runCommand("team add pointers");
			server.runCommand("team modify pointers color red");
			server.runCommand("team join pointers @a");
			int red = net.minecraft.network.chat.TextColor.fromLegacyFormat(ChatFormatting.RED).getValue();
			check(server.computeOnServer(s -> Pointing.colorOf(connection.getServerPlayer())) == red, "a player on a red team is not red");

			UUID player = server.computeOnServer(s -> connection.getServerPlayer().getUUID());
			check(server.computeOnServer(s -> Pointing.point(connection.getServerPlayer())), "the point was refused");
			check(server.computeOnServer(s -> Pointing.pointing(player)), "nothing is being pointed at");
			check(!server.computeOnServer(s -> Pointing.point(connection.getServerPlayer())), "a second press straight after the first pointed again");
			context.waitTicks(3);
			justfatlard.pandorical.gametest.Pictures.frame(context, connection, "pointing");
			context.waitTicks(70);
			check(!server.computeOnServer(s -> Pointing.pointing(player)), "the point outlasted its time");
			server.runCommand("team remove pointers");
		}
	}

	private static void check(boolean holds, String otherwise) {
		if (!holds) throw new AssertionError(otherwise);
	}
}
