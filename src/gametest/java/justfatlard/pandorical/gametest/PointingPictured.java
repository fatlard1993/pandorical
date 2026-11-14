package justfatlard.pandorical.gametest;

import justfatlard.pandorical.point.Pointing;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

/**
 * The README's picture of pointing: someone in a field points at a sheep, and the sheep is outlined
 * in their team's colour with the line running to it from them. Only with {@code -Ppictures}.
 *
 * <p>The camera is a spectator, and a spectator cannot point, so the camera player points for the
 * figure: for one server tick it stands where the figure stands, in creative, looking at the sheep,
 * points, and goes back. The motes are sent from there, so the line runs from the figure's chest.
 *
 * <p>Taken straight after the point rather than through {@link Pictures#shoot}, whose settling
 * takes longer than the motes last: the line is drawn only in a point's first moments, and its
 * redraw would start from wherever the camera player has gone back to. The scene is settled first.
 */
public final class PointingPictured implements FabricClientGameTest {
	private static final String NAME = "pointing-at-things";

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!Pictures.wanted()) return;
		try (TestSingleplayerContext world = Pictures.world(context, 1L)) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			Pictures.stage(context, server, Pictures.MORNING);
			server.runCommand("team add pointers");
			server.runCommand("team modify pointers color light_purple");
			server.runCommand("team join pointers @a");

			BlockPos spawn = server.computeOnServer(s -> connection.getServerPlayer().blockPosition());
			BlockPos at = server.computeOnServer(s -> {
				BlockPos open = Pictures.flattest(s.overworld(), spawn, "minecraft:plains", 512, 24);
				return open != null ? open : spawn;
			});
			// There first, so the scene is set in chunks that stay loaded: built from afar, its figure
			// and sheep were saved away and came back as new entities by the time the camera arrived.
			Pictures.look(server, Vec3.atBottomCenterOf(at).add(0, 6, -8), Vec3.atBottomCenterOf(at));
			Pictures.settle(context, connection);
			UUID[] figure = new UUID[1], sheepId = new UUID[1];
			Vec3[] scene = server.computeOnServer(s -> {
				ServerLevel level = s.overworld();
				Pictures.clearAround(level, at, 13, 10);
				int base = level(level, Pictures.ground(level, at.getX(), at.getZ()), 9);
				Vec3 pointer = new Vec3(at.getX() - 3 + 0.5, base, at.getZ() + 0.5);
				Vec3 sheep = new Vec3(at.getX() + 3 + 0.5, base, at.getZ() + 0.5);

				Mob mob = EntityTypes.SHEEP.create(level, EntitySpawnReason.COMMAND);
				float turned = yawTo(sheep, pointer) - 25F;
				mob.snapTo(sheep.x, sheep.y, sheep.z, turned, 0F);
				face(mob, turned);
				mob.setNoAi(true);
				mob.setPersistenceRequired();
				level.addFreshEntity(mob);

				Mannequin stand = EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.COMMAND);
				float yaw = yawTo(pointer, sheep);
				stand.snapTo(pointer.x, pointer.y, pointer.z, yaw, 10F);
				face(stand, yaw);
				level.addFreshEntity(stand);
				figure[0] = stand.getUUID();
				sheepId[0] = mob.getUUID();
				return new Vec3[] {pointer, mob.getBoundingBox().getCenter()};
			});
			Vec3 pointer = scene[0], target = scene[1];

			Vec3 middle = pointer.lerp(target, 0.5).add(0, 0.3, 0);
			Vec3 camera = server.computeOnServer(s -> Pictures.vantage(s.overworld(), middle, 5.5, -0.55, -30));
			if (camera == null) throw new AssertionError("nowhere to stand the camera that sees the field");
			// Nearly level, not down at the grass: the two sit across the middle with the field behind them.
			Pictures.look(server, camera, middle.add(0, 0.55, 0));
			Pictures.settle(context, connection);

			boolean pointed = server.computeOnServer(s -> {
				ServerLevel level = s.overworld();
				ServerPlayer player = connection.getServerPlayer();
				var stand = level.getEntity(figure[0]);
				if (stand == null) throw new AssertionError("the figure is gone from the field");
				Vec3 back = player.position();
				float yaw = player.getYRot(), pitch = player.getXRot();
				// Out of the way of the trace, which starts inside it; back before anyone is told it moved.
				stand.setPos(pointer.add(0, 4, 0));
				player.setGameMode(GameType.CREATIVE);
				Vec3 eye = pointer.add(0, player.getEyeHeight(), 0);
				player.teleportTo(level, pointer.x, pointer.y, pointer.z, Set.of(), yawTo(pointer, target), pitchTo(eye, target), false);
				// What it would point at is the sheep, not a tuft of ground on the way to it.
				boolean done = Pointing.trace(player) instanceof EntityHitResult hit && hit.getEntity().getUUID().equals(sheepId[0])
					&& Pointing.point(player);
				player.teleportTo(level, back.x, back.y, back.z, Set.of(), yaw, pitch, false);
				player.setGameMode(GameType.SPECTATOR);
				stand.setPos(pointer);
				return done;
			});
			if (!pointed) throw new AssertionError("the point at the sheep missed it or was refused");
			connection.waitForClientboundPackets();
			context.waitTicks(2);
			context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
			Path shot = context.takeScreenshot(TestScreenshotOptions.of(NAME).withSize(Pictures.WIDTH, Pictures.HEIGHT).disableCounterPrefix());
			keep(shot);
			System.out.println("[pandorical test] picture " + shot.toAbsolutePath());
			server.runOnServer(s -> Pointing.forget(connection.getServerPlayer().getUUID()));
			server.runCommand("team remove pointers");
		}
	}

	/**
	 * Level ground round {@code centre}, {@code radius} across, so nothing thin or raised stands
	 * between the figure and the sheep: raised to its highest column with grass over dirt, never cut
	 * down, so what edge it has drops away from the camera instead of standing behind the scene as
	 * a wall of dirt. The height stood on.
	 */
	private static int level(ServerLevel level, BlockPos centre, int radius) {
		int base = centre.getY();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx * dx + dz * dz <= radius * radius) {
					base = Math.max(base, Pictures.ground(level, centre.getX() + dx, centre.getZ() + dz).getY());
				}
			}
		}
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx * dx + dz * dz > radius * radius) continue;
				int x = centre.getX() + dx, z = centre.getZ() + dz;
				int top = Pictures.ground(level, x, z).getY();
				for (int y = top - 1; y < base - 1; y++) level.setBlock(new BlockPos(x, y, z), Blocks.DIRT.defaultBlockState(), 2 | 16);
				level.setBlock(new BlockPos(x, base - 1, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2 | 16);
				for (int y = base; y < base + 8; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2 | 16);
			}
		}
		return base;
	}

	private static void face(LivingEntity entity, float yaw) {
		entity.setYHeadRot(yaw);
		entity.setYBodyRot(yaw);
	}

	/** Yaw as the game measures it: 0 faces +z, 90 faces -x. */
	private static float yawTo(Vec3 from, Vec3 to) {
		return (float) -Math.toDegrees(Math.atan2(to.x - from.x, to.z - from.z));
	}

	private static float pitchTo(Vec3 eye, Vec3 to) {
		double dx = to.x - eye.x, dy = to.y - eye.y, dz = to.z - eye.z;
		return (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
	}

	/** Into the mod's screenshots/ folder, as {@link Pictures#shoot} keeps its shots. */
	private static void keep(Path shot) {
		// The test runs in <mod>/build/run/clientGameTest.
		Path folder = Path.of("").toAbsolutePath().getParent().getParent().getParent().resolve("screenshots");
		try {
			Files.createDirectories(folder);
			Files.copy(shot, folder.resolve(NAME + ".png"), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new AssertionError("could not keep the picture in " + folder, e);
		}
	}
}
