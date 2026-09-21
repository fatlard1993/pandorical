package justfatlard.pandorical.gametest;

import justfatlard.pandorical.api.PandoricalApi;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/**
 * The shared half of every mod's tests: get into a world, and fail loudly if anything on the way
 * threw.
 *
 * <p>Every mod in the suite needs the same first test - does it load, do its mixins bind, does a
 * world come up with it installed - and writing that out sixty-eight times would mean sixty-eight
 * chances to write it differently. So it lives here, where they all already compile against it.
 *
 * <p>A test that gets this far has already proved a good deal without asserting anything: a mixin
 * that no longer applies refuses the mod outright, a registration that throws takes the client down
 * with it, and a datapack that will not parse fails the world. Those are most of the ways a mod in
 * this suite actually breaks, and all of them were being checked by hand until now.
 *
 * <p>What it cannot prove is anything that needs somebody to do something. For that a mod passes
 * its own {@link Session} body, which runs with a real player in a real world - and an uncaught
 * exception on the server thread ends the test, which is exactly how a crash on the first tick of
 * an emote would have been caught before it shipped.
 */
public final class Smoke {
	private Smoke() {}

	/** Chosen by {@code -Ptest.level=full}; anything else is a smoke run. */
	private static final String LEVEL = System.getProperty("pandorical.test.level", "smoke");

	/**
	 * Whether this is the thorough run.
	 *
	 * <p>Two levels because they answer different questions and cost different amounts. A smoke run
	 * is "is this mod broken", is meant to be run over the whole suite before a deploy, and has to
	 * stay short enough that somebody actually does. A full run is "does this mod work", drives
	 * screens and clicks, and is worth the minutes when the mod itself has changed.
	 */
	public static boolean full() {
		return "full".equalsIgnoreCase(LEVEL);
	}

	/** A world that is up, with a player in it, for the length of one test. */
	public static final class Session {
		private final ClientGameTestContext context;
		private final TestSingleplayerContext world;
		private final TestServerConnection connection;

		private Session(ClientGameTestContext context, TestSingleplayerContext world,
				TestServerConnection connection) {
			this.context = context;
			this.world = world;
			this.connection = connection;
		}

		public ClientGameTestContext context() { return context; }

		public ServerPlayer player() { return connection.getServerPlayer(); }

		public boolean full() { return Smoke.full(); }

		public void waitTicks(int ticks) { context.waitTicks(ticks); }

		/** Run something on the server thread, and wait for it. */
		public void onServer(Consumer<MinecraftServer> what) {
			world.getServer().runOnServer(what::accept);
		}

		/**
		 * Run a command as the player, exactly as typing it would.
		 *
		 * <p>Through the player's own source rather than the console's, so a command that refuses
		 * the console, or that reads where the caller is standing, behaves as it does in play.
		 */
		public void command(String command) {
			onServer(server -> server.getCommands().performPrefixedCommand(
				player().createCommandSourceStack(), command.startsWith("/")
					? command.substring(1) : command));
		}
	}

	/** A mod that loads and reaches a world, with nothing else asked of it. */
	public static void run(ClientGameTestContext context, String modId) {
		run(context, modId, session -> {});
	}

	/**
	 * @param modId the mod under test, checked as loaded before anything else: a test source set
	 *              that has quietly stopped including its own mod would otherwise pass by testing
	 *              nothing at all
	 * @param body  what this mod wants doing with a world and a player, once there is one
	 */
	public static void run(ClientGameTestContext context, String modId, Consumer<Session> body) {
		if (!FabricLoader.getInstance().isModLoaded(modId)) {
			throw new AssertionError(modId + " is not loaded: the test is testing nothing");
		}

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			world.getServer().waitFor(server -> PandoricalApi.isAvailable(connection.getServerPlayer()));
			// Long enough for anything that registers on join to have done so and thrown if it was
			// going to.
			context.waitTicks(20);

			body.accept(new Session(context, world, connection));

			// After the body, because the thing it did may only fall over on a later tick - which
			// is precisely what a burst of particles counting itself down does.
			context.waitTicks(40);
		}
		// Closing a world while it is still coming out of a pause deadlocks the harness's own tick
		// lock; the existing tests learned this the hard way.
		context.waitTicks(10);
	}
}
