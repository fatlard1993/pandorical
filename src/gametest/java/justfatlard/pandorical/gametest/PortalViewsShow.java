package justfatlard.pandorical.gametest;

import justfatlard.pandorical.client.portal.PortalViews;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.PortalApi;
import justfatlard.pandorical.portal.ImmersivePortals;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;

/**
 * A portal that has been through once shows the nether past it: the client is sent the far side and
 * draws it behind the swirl. Gold stands either side of the nether portal so the shots can be read.
 * With immersive portals off the view goes.
 */
public final class PortalViewsShow implements FabricClientGameTest {
	/** The far end this test's own answer gives the overworld portal; null leaves it to pairing. */
	private static volatile PortalApi.FarEnd oneFace;
	private static BlockPos netherPortal;
	private static Direction glowstoneSide;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = Pictures.world(context, 1L)) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			server.runOnServer(s -> ImmersivePortals.choose(s, true));
			PandoricalApi.portals().lookThrough((viewer, level, pos) -> level.dimension() == Level.OVERWORLD ? oneFace : null);

			BlockPos spawn = server.computeOnServer(s -> connection.getServerPlayer().blockPosition());
			BlockPos base = server.computeOnServer(s -> {
				BlockPos open = Pictures.flattest(s.overworld(), spawn, "minecraft:plains", 512, 24);
				BlockPos at = open != null ? open : spawn;
				Pictures.clearAround(s.overworld(), at, 10, 10);
				BlockPos ground = Pictures.ground(s.overworld(), at.getX(), at.getZ());
				buildPortal(s.overworld(), ground);
				return ground;
			});

			travel(context, server, connection, Level.OVERWORLD, base.above(), Level.NETHER);
			server.runOnServer(s -> markFarSide(s.getLevel(Level.NETHER), connection.getServerPlayer().blockPosition()));
			BlockPos nether = server.computeOnServer(s -> connection.getServerPlayer().blockPosition());
			travel(context, server, connection, Level.NETHER, nether, Level.OVERWORLD);

			Pictures.stage(context, server, Pictures.MORNING);
			Vec3 middle = Vec3.atBottomCenterOf(base).add(0.5, 2, 0);
			Pictures.look(server, middle.add(0, -1.2, -5), middle);
			waitForView(context, true);
			Path on = Pictures.shoot(context, connection, "portal-view-front");
			double plane = base.getZ() + 0.5;
			Vec3 front = new Vec3(middle.x, middle.y, plane - 5), back = new Vec3(middle.x, middle.y, plane + 5);
			allBeyond(context, front, plane);
			allBeyond(context, back, plane);
			Pictures.look(server, middle.add(-3.5, -1.2, -3.5), middle);
			context.waitTicks(20);
			Pictures.shoot(context, connection, "portal-view-angle");
			Pictures.look(server, middle.add(0, -1.2, 5), middle);
			context.waitTicks(20);
			Pictures.shoot(context, connection, "portal-view-back");

			// Out of one face only: both sides of the near sheet show what stands in front of it
			oneFace = PortalApi.FarEnd.outOf(GlobalPos.of(Level.NETHER, netherPortal), glowstoneSide);
			context.waitTicks(30);
			int fromFront = allBeyond(context, front, plane), fromBack = allBeyond(context, back, plane);
			if (fromFront != fromBack) throw new AssertionError("one face showed " + fromFront + " blocks from the front and " + fromBack + " from the back");
			oneFace = null;
			context.waitTicks(30);

			Pictures.look(server, middle.add(0, -1.2, -5), middle);
			server.runOnServer(s -> ImmersivePortals.choose(s, false));
			waitForView(context, false);
			Path off = Pictures.shoot(context, connection, "portal-view-off");
			double changed = difference(on, off);
			if (changed < 6.0) throw new AssertionError("the view changed the portal by only " + changed + " on average: it is not showing");
		}
	}

	/** Every far block an eye here sees stands past the sheet from it; how many there are. */
	private static int allBeyond(ClientGameTestContext context, Vec3 eye, double plane) {
		List<Vec3> shown = context.computeOnClient(client -> PortalViews.shownFrom(eye));
		if (shown.isEmpty()) throw new AssertionError("nothing shows past the sheet from " + eye);
		for (Vec3 at : shown) {
			if ((at.z - plane) * (eye.z - plane) >= 0) throw new AssertionError("a far block at " + at + " stands on the eye's side of the sheet, from " + eye);
		}
		return shown.size();
	}

	/** How far apart two shots are in the middle of the frame, where the sheet stands: mean of each channel's difference. */
	private static double difference(Path a, Path b) {
		try {
			BufferedImage one = ImageIO.read(a.toFile()), two = ImageIO.read(b.toFile());
			int left = one.getWidth() / 2 - 100, top = one.getHeight() / 2 - 200;
			long total = 0;
			int count = 0;
			for (int x = left; x < left + 200; x += 2) {
				for (int y = top; y < top + 300; y += 2) {
					int p = one.getRGB(x, y), q = two.getRGB(x, y);
					for (int shift = 0; shift <= 16; shift += 8) total += Math.abs(((p >> shift) & 0xFF) - ((q >> shift) & 0xFF));
					count += 3;
				}
			}
			return (double) total / count;
		} catch (IOException e) {
			throw new AssertionError("could not read the shots", e);
		}
	}

	private static void waitForView(ClientGameTestContext context, boolean shown) {
		for (int waited = 0; waited < 200; waited += 5) {
			if ((context.computeOnClient(client -> PortalViews.showing()) > 0) == shown) return;
			context.waitTicks(5);
		}
		throw new AssertionError(shown ? "no view of the far side arrived" : "the view stayed after immersive portals were turned off");
	}

	/** Stand in the portal near {@code entry} and wait to arrive. */
	private static void travel(ClientGameTestContext context, TestServerContext server, TestServerConnection connection,
							   ResourceKey<Level> from, BlockPos entry, ResourceKey<Level> to) {
		context.waitTicks(40);
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			ServerLevel level = s.getLevel(from);
			BlockPos inside = portalBlockNear(level, entry);
			if (inside == null) throw new AssertionError("no portal at " + entry + " in " + from.identifier());
			player.setPortalCooldown(0);
			player.teleportTo(level, inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5, Set.of(), 0, 0, true);
		});
		server.waitFor(s -> connection.getServerPlayer().level().dimension() == to, 400);
		context.waitTicks(20);
	}

	/** Gold columns three blocks out either side of the nether portal the player stands in. */
	private static void markFarSide(ServerLevel nether, BlockPos inside) {
		BlockPos portal = portalBlockNear(nether, inside);
		if (portal == null) throw new AssertionError("arrived nowhere near a nether portal");
		boolean alongX = nether.getBlockState(portal).getValue(NetherPortalBlock.AXIS) == Direction.Axis.X;
		netherPortal = portal;
		glowstoneSide = alongX ? Direction.SOUTH : Direction.EAST;
		BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();
		for (int side : new int[] {-3, 3}) {
			for (int up = 0; up < 3; up++) {
				BlockPos at = alongX ? portal.offset(0, up, side) : portal.offset(side, up, 0);
				nether.setBlock(at, gold, 2);
			}
		}
		nether.setBlock(alongX ? portal.offset(1, 0, 5) : portal.offset(5, 0, 1), Blocks.GLOWSTONE.defaultBlockState(), 2);
	}

	private static BlockPos portalBlockNear(ServerLevel level, BlockPos around) {
		for (BlockPos pos : BlockPos.betweenClosed(around.offset(-3, -3, -3), around.offset(3, 3, 3))) {
			if (level.getBlockState(pos).is(Blocks.NETHER_PORTAL)) return pos.immutable();
		}
		return null;
	}

	/** A 2 wide, 3 tall sheet facing south, its frame standing on {@code ground}. */
	private static void buildPortal(ServerLevel level, BlockPos ground) {
		BlockState portal = Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.X);
		for (int x = -1; x <= 2; x++) {
			for (int y = 0; y <= 4; y++) {
				boolean frame = x == -1 || x == 2 || y == 0 || y == 4;
				level.setBlock(ground.offset(x, y, 0), frame ? Blocks.OBSIDIAN.defaultBlockState() : portal, 2 | 16);
			}
		}
	}
}
