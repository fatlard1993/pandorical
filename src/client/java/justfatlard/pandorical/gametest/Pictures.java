package justfatlard.pandorical.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Pictures for a mod's README and download pages, taken by its game tests.
 *
 * <p>Each mod's showcase used to set its own scene, and most came out the same way: a superflat
 * void with its edges showing, the subject small in the middle of a lot of sky, and chat, the
 * notice badge or a crosshair over the top. This is that setup done once. A generated world,
 * the same one every run; the HUD and chat gone; the light and weather held; the camera put where
 * it is aimed rather than guessed at; and the frame taken only once the chunks in it have drawn.
 *
 * <p>Run with {@code -Ppictures} and each shot is also written into the mod's own
 * {@code screenshots/} folder, which is where its README and its download pages take them from.
 * Without it, shots go only to the test's run folder, so an ordinary test run leaves the
 * repository as it found it.
 */
public final class Pictures {
	private Pictures() {}

	public static final int WIDTH = 1920;
	public static final int HEIGHT = 1080;

	/** Mid-morning: the sun high enough to light a scene from the side without washing it out. */
	public static final int MORNING = 2000;
	/** Late afternoon, for a warmer light and longer shadows. */
	public static final int AFTERNOON = 10000;

	/**
	 * A generated world, not a superflat one: normal terrain from a fixed seed, so a scene found in
	 * it is found again on the next run. Commands are on, for setting the scene.
	 */
	public static TestSingleplayerContext world(ClientGameTestContext context, long seed) {
		return context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(creation -> {
				creation.setSeed(Long.toString(seed));
				creation.setAllowCommands(true);
				creation.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
			})
			.create();
	}

	/**
	 * Ready the frame: no HUD, crosshair, hand or chat; clear weather; the clock stopped at
	 * {@code time}; and far enough to see that the distance is land rather than fog.
	 *
	 * <p>Mob spawning is off, so a scene holds only what a test put in it.
	 */
	public static void stage(ClientGameTestContext context, TestServerContext server, int time) {
		// Set through the rules themselves, not typed as commands: 26 renamed every one of them
		// (doDaylightCycle is advance_time now), and a command with an old name fails into chat
		// where nobody reads it, leaving the clock running under a test that thinks it stopped.
		// A rename here is a compile error instead.
		server.runOnServer(s -> {
			GameRules rules = s.overworld().getGameRules();
			rules.set(GameRules.ADVANCE_TIME, false, s);
			rules.set(GameRules.ADVANCE_WEATHER, false, s);
			rules.set(GameRules.SPAWN_MOBS, false, s);
			// The camera is a spectator, and a spectator that may not generate chunks waits
			// forever anywhere the world has not been yet.
			rules.set(GameRules.SPECTATORS_GENERATE_CHUNKS, true, s);
		});
		server.runCommand("weather clear");
		server.runCommand("time set " + time);
		server.runCommand("gamemode spectator @a");
		context.runOnClient(client -> {
			if (!client.gui.hud.isHidden()) client.gui.hud.toggle();
			client.options.renderDistance().set(RENDER_DISTANCE);
			// And tell the server, which sends chunks as far as the client last said it wants them:
			// setting the option alone left it sending the old, small radius forever.
			client.options.broadcastOptions();
			client.gui.hud.getChat().clearMessages(false);
		});
	}

	/** The highest solid surface at a column: where to stand a scene in generated terrain. */
	public static BlockPos ground(ServerLevel level, int x, int z) {
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
	}

	/**
	 * The flattest open ground of a biome near a point: the middle of a {@code size}-wide patch whose
	 * terrain varies least, above sea level, searched {@code reach} blocks round {@code from}.
	 *
	 * <p>Asked of the world generator, which knows how high the land will be without building any
	 * of it, so the search costs nothing however far it looks. A subject set on it stands on level
	 * ground with room round it, instead of against a hillside or among other trees that a scene
	 * then has to work around - the ancient code reads the logs near a tree, and a spruce beside an
	 * oak grew it into a spruce. Null if the biome is not found.
	 */
	public static BlockPos flattest(ServerLevel level, BlockPos from, String biome, int reach, int size) {
		ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, Identifier.parse(biome));
		var generator = level.getChunkSource().getGenerator();
		var random = level.getChunkSource().randomState();
		int sea = generator.getSeaLevel();
		BlockPos best = null;
		int bestSpread = Integer.MAX_VALUE;
		int step = Math.max(8, size / 2);
		for (int x = from.getX() - reach; x <= from.getX() + reach; x += step) {
			for (int z = from.getZ() - reach; z <= from.getZ() + reach; z += step) {
				if (!level.getBiome(new BlockPos(x, sea, z)).is(key)) continue;
				int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
				for (int dx = -size / 2; dx <= size / 2; dx += 4) {
					for (int dz = -size / 2; dz <= size / 2; dz += 4) {
						int h = generator.getBaseHeight(x + dx, z + dz, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
						low = Math.min(low, h);
						high = Math.max(high, h);
					}
				}
				if (low <= sea + 1) continue;
				if (high - low < bestSpread) {
					bestSpread = high - low;
					best = new BlockPos(x, high, z);
				}
			}
		}
		return best;
	}

	/**
	 * Clear a stage: every tree, bush and tall plant within {@code radius} of {@code centre} and up
	 * to {@code height} above it taken away, the ground left as it was. So what a scene puts there
	 * stands on its own, without a stray birch in front of it or grass blades across the lens.
	 */
	public static void clearAround(ServerLevel level, BlockPos centre, int radius, int height) {
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-radius, -2, -radius), centre.offset(radius, height, radius))) {
			int dx = pos.getX() - centre.getX(), dz = pos.getZ() - centre.getZ();
			if (dx * dx + dz * dz > radius * radius) continue;
			var state = level.getBlockState(pos);
			if (state.is(net.minecraft.tags.BlockTags.LOGS) || state.is(net.minecraft.tags.BlockTags.LEAVES)
					|| state.is(net.minecraft.tags.BlockTags.REPLACEABLE) && !state.isAir() && state.getFluidState().isEmpty()
					|| state.is(net.minecraft.tags.BlockTags.FLOWERS) || state.is(net.minecraft.tags.BlockTags.SAPLINGS)) {
				level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2 | 16);
			}
		}
	}

	/** The top of whatever stands at a column, leaves included: the height of a forest's canopy. */
	public static BlockPos canopy(ServerLevel level, int x, int z) {
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));
	}

	/**
	 * Solid footing at or near a column: the nearest surface within {@code reach} that is not water
	 * or lava, so a scene is not set up on a lake. Null if every column in reach is wet.
	 */
	public static BlockPos dryGround(ServerLevel level, int x, int z, int reach) {
		return dryGround(level, x, z, reach, below -> below.isSolid());
	}

	/** Dry footing whose block underfoot passes {@code footing}: soil for a tree, say. */
	public static BlockPos dryGround(ServerLevel level, int x, int z, int reach,
			java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> footing) {
		for (int r = 0; r <= reach; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
					BlockPos top = ground(level, x + dx, z + dz);
					if (level.getFluidState(top).isEmpty() && level.getFluidState(top.below()).isEmpty()
							&& footing.test(level.getBlockState(top.below()))) return top;
				}
			}
		}
		return null;
	}

	/**
	 * Where to stand the camera to see {@code target}: about {@code distance} away and {@code rise}
	 * above it, in open air, with nothing between the eye and the subject.
	 *
	 * <p>Tried round the compass from the angle asked for, and higher each time round, because a
	 * fixed offset in generated terrain lands inside a hill as often as not: the camera then looks
	 * out from underground and the picture is the inside of the world. Null if no spot in reach can
	 * see it.
	 *
	 * @param degrees the direction to look from, measured as yaw is: 0 is south of the target
	 */
	public static Vec3 vantage(ServerLevel level, Vec3 target, double distance, double rise, double degrees) {
		for (int lift = 0; lift <= 24; lift += 6) {
			for (int k = 0; k < 12; k++) {
				// 0, +30, -30, +60, -60 and on round: nearest to the angle asked for first.
				int turn = (k + 1) / 2 * 30 * (k % 2 == 1 ? 1 : -1);
				double angle = Math.toRadians(degrees + turn);
				Vec3 from = target.add(-Math.sin(angle) * distance, rise + lift, Math.cos(angle) * distance);
				BlockPos feet = BlockPos.containing(from);
				if (!level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()) continue;
				Vec3 eye = from.add(0, 1.62, 0);
				var hit = level.clip(new ClipContext(eye, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
					CollisionContext.empty()));
				if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS
						|| hit.getLocation().distanceTo(target) < 3) return from;
			}
		}
		return null;
	}

	/**
	 * The nearest place of a biome to a point, by the biome's id ({@code "minecraft:cherry_grove"}),
	 * on the surface. Null if there is none within reach.
	 */
	public static BlockPos near(ServerLevel level, BlockPos from, String biome) {
		ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, Identifier.parse(biome));
		var found = level.findClosestBiome3d((Holder<Biome> holder) -> holder.is(key), from, 6400, 32, 64);
		return found == null ? null : ground(level, found.getFirst().getX(), found.getFirst().getZ());
	}

	/**
	 * Stand the camera at {@code from} and point it at {@code at}. The camera is a spectator, so it
	 * floats where it is put; its eye is 1.62 above the position given.
	 */
	public static void look(TestServerContext server, Vec3 from, Vec3 at) {
		double dx = at.x - from.x;
		double dy = at.y - (from.y + 1.62);
		double dz = at.z - from.z;
		double yaw = -Math.toDegrees(Math.atan2(dx, dz));
		double pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		server.runCommand("tp @a %.2f %.2f %.2f %.1f %.1f".formatted(from.x, from.y, from.z, yaw, pitch));
	}

	/**
	 * Wait until every chunk in view has drawn, and a moment more.
	 *
	 * <p>For as long as it takes, so long as it is getting somewhere. Arriving where the world has
	 * not been yet means generating it and drawing it without a graphics card, and on a machine
	 * busy with other builds that has taken many minutes; any fixed limit was either too short on a
	 * busy day or a long wait for a wait that would never end. So it says how far it has got as it
	 * goes, and gives up only once nothing has arrived for {@link #STALL} ticks.
	 */
	public static void settle(ClientGameTestContext context, TestServerConnection connection) {
		// First let the client catch up with a teleport: asked straight after one, it still stands
		// where it was, where everything is loaded, and says there is nothing to wait for.
		connection.waitForClientboundPackets();
		context.waitTicks(10);
		int missing = Integer.MAX_VALUE;
		int stalled = 0;
		while (true) {
			int now = context.computeOnClient(Pictures::missingChunks);
			boolean drawn = context.computeOnClient(client -> client.levelRenderer.hasRenderedAllSections());
			if (now == 0 && drawn) break;
			System.out.println("[Pictures] waiting on " + now + " chunks to arrive" + (drawn ? "" : ", and the rest to draw"));
			stalled = now < missing ? 0 : stalled + CHECK;
			missing = Math.min(missing, now);
			if (stalled >= STALL) throw new AssertionError("chunks stopped arriving with " + now + " still to come");
			context.waitTicks(CHECK);
		}
		context.waitTicks(20);
	}

	/**
	 * How many chunks the server should have sent and the client does not have yet. By the game's
	 * own measure of what is in view, which is round: Fabric's wait counts a square, and its four
	 * corners never come, so it waited out every limit it was given.
	 */
	private static int missingChunks(net.minecraft.client.Minecraft client) {
		int radius = client.options.getEffectiveRenderDistance();
		var centre = client.player.chunkPosition();
		int missing = 0;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				int x = centre.x() + dx, z = centre.z() + dz;
				if (!net.minecraft.server.level.ChunkTrackingView.isInViewDistance(centre.x(), centre.z(), radius, x, z)) continue;
				if (client.level.getChunk(x, z, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) == null) missing++;
			}
		}
		return missing;
	}

	/** Ticks between checks: five seconds. */
	private static final int CHECK = 100;
	/** Ticks with nothing arriving before a wait is called off: five minutes. */
	private static final int STALL = 6000;

	/**
	 * Chunks: a hundred and sixty blocks, which is land to the horizon in a picture, and a quarter
	 * of what sixteen asks the server to generate when the camera arrives somewhere new.
	 */
	private static final int RENDER_DISTANCE = 10;

	/**
	 * Take the picture, once every chunk in view has drawn and the scene has had a moment to
	 * settle, and with chat cleared again in case anything spoke since {@link #stage}.
	 *
	 * @return where the test framework saved it
	 */
	public static Path shoot(ClientGameTestContext context, TestServerConnection connection, String name) {
		settle(context, connection);
		context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
		context.waitTick();
		Path shot = context.takeScreenshot(TestScreenshotOptions.of(name).withSize(WIDTH, HEIGHT).disableCounterPrefix());
		keep(shot, name);
		return shot;
	}

	/**
	 * Open a block's screen as the player would: standing at it in creative (a spectator cannot
	 * open anything) and using it, then waiting for the screen to be up. Fill the block before, or
	 * the open menu's slots after, with {@code server.runOnServer}.
	 */
	public static void open(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, BlockPos block) {
		server.runCommand("gamemode creative @a");
		look(server, Vec3.atBottomCenterOf(block).add(0, 0, 2.5), Vec3.atCenterOf(block));
		settle(context, connection);
		// Through the server's own use of a block, as a player's click arrives, not the block's
		// handler directly: a mod that opens its screen from Fabric's use event (a fletching table
		// that becomes a bench) is only asked on this path.
		server.runOnServer(s -> {
			var player = connection.getServerPlayer();
			player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(), net.minecraft.world.InteractionHand.MAIN_HAND,
				new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(block), net.minecraft.core.Direction.SOUTH, block, false));
		});
		context.waitFor(client -> client.gui.screen() != null);
		context.waitTicks(10);
	}

	/** Put a kit in the player's pack, so the player's half of a screen is not empty. */
	public static void carry(TestServerContext server, TestServerConnection connection, net.minecraft.world.item.ItemStack... kit) {
		server.runOnServer(s -> {
			var pack = connection.getServerPlayer().getInventory();
			for (int i = 0; i < kit.length; i++) pack.setItem(i, kit[i]);
		});
	}

	/**
	 * Take the picture of a screen: the menu, chest or book that is open, trimmed to the panel with
	 * a margin round it, rather than a small panel in a wide dark frame. A store page shows it at a
	 * fraction of its size, and the panel is all there is to see. The GUI scale is already the
	 * largest a 1920 by 1080 window allows, so trimming is what makes the panel fill the picture.
	 */
	public static Path shootScreen(ClientGameTestContext context, TestServerConnection connection, String name) {
		// The mouse to the corner, off the panel: left where it was, it lights up whichever slot it
		// happens to sit over.
		context.runOnClient(client -> {
			try {
				for (String axis : new String[] {"xpos", "ypos"}) {
					var field = net.minecraft.client.MouseHandler.class.getDeclaredField(axis);
					field.setAccessible(true);
					field.setDouble(client.mouseHandler, 0);
				}
			} catch (ReflectiveOperationException e) {
				throw new AssertionError("could not move the mouse off the panel", e);
			}
		});
		context.waitTicks(2);
		int[] panel = context.computeOnClient(Pictures::panel);
		Path shot = shoot(context, connection, name);
		if (panel != null) crop(shot, panel);
		keep(shot, name);
		return shot;
	}

	/** Pixels of the world kept round a cropped screen. */
	private static final int MARGIN = 48;

	/**
	 * Where the open screen's panel is, in the window's pixels: x, y, width, height, and then the
	 * window's own width and height, which the picture may not share. A container screen says so
	 * itself; a Pandorical screen centres a panel of the size it was sent. Null for anything else,
	 * which is then pictured whole.
	 */
	private static int[] panel(net.minecraft.client.Minecraft client) {
		var screen = client.gui.screen();
		double scale = client.getWindow().getGuiScale();
		try {
			int x, y, w, h;
			if (screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> container) {
				Class<?> type = net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class;
				x = field(type, container, "leftPos");
				y = field(type, container, "topPos");
				w = field(type, container, "imageWidth");
				h = field(type, container, "imageHeight");
			} else if (screen instanceof justfatlard.pandorical.client.screen.PandoricalScreen pandorical) {
				var definition = justfatlard.pandorical.client.screen.PandoricalScreen.class.getDeclaredField("screenDef");
				definition.setAccessible(true);
				var def = (justfatlard.pandorical.protocol.OpenScreenS2C) definition.get(pandorical);
				w = def.width();
				h = def.height();
				x = (screen.width - w) / 2;
				y = (screen.height - h) / 2;
			} else {
				return null;
			}
			return new int[] {(int) (x * scale), (int) (y * scale), (int) (w * scale), (int) (h * scale),
				client.getWindow().getWidth(), client.getWindow().getHeight()};
		} catch (ReflectiveOperationException e) {
			return null;
		}
	}

	private static int field(Class<?> type, Object owner, String name) throws ReflectiveOperationException {
		var field = type.getDeclaredField(name);
		field.setAccessible(true);
		return field.getInt(owner);
	}

	/** Cut a picture down to a rectangle and {@link #MARGIN} round it. */
	private static void crop(Path shot, int[] panel) {
		try {
			java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(shot.toFile());
			// The picture may be taken at a different size from the window the GUI was laid out in.
			double sx = image.getWidth() / (double) panel[4], sy = image.getHeight() / (double) panel[5];
			int x = Math.max(0, (int) (panel[0] * sx) - MARGIN);
			int y = Math.max(0, (int) (panel[1] * sy) - MARGIN);
			int right = Math.min(image.getWidth(), (int) ((panel[0] + panel[2]) * sx) + MARGIN);
			int bottom = Math.min(image.getHeight(), (int) ((panel[1] + panel[3]) * sy) + MARGIN);
			javax.imageio.ImageIO.write(image.getSubimage(x, y, right - x, bottom - y), "png", shot.toFile());
		} catch (IOException e) {
			throw new AssertionError("could not crop " + shot, e);
		}
	}

	/** With {@code -Ppictures}, into the mod's {@code screenshots/} folder as well. */
	private static void keep(Path shot, String name) {
		if (!Boolean.getBoolean("pandorical.pictures")) return;
		// The test runs in <mod>/build/run/clientGameTest.
		Path folder = Path.of("").toAbsolutePath().getParent().getParent().getParent().resolve("screenshots");
		try {
			Files.createDirectories(folder);
			Files.copy(shot, folder.resolve(name + ".png"), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new AssertionError("could not keep the picture " + name + " in " + folder, e);
		}
	}
}
