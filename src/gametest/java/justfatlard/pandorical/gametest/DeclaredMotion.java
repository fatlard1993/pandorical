package justfatlard.pandorical.gametest;

import justfatlard.pandorical.api.Capabilities;
import justfatlard.pandorical.api.ComponentBuilder;
import justfatlard.pandorical.api.ComponentType;
import justfatlard.pandorical.api.ComponentUpdateBuilder;
import justfatlard.pandorical.api.HudBuilder;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.client.animation.ClientDisplaySpins;
import justfatlard.pandorical.client.component.AbstractComponent;
import justfatlard.pandorical.client.component.MapComponent;
import justfatlard.pandorical.client.hud.HudManager;
import justfatlard.pandorical.client.hud.HudOverlay;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import org.joml.Vector3f;

import java.util.List;

/**
 * Motion declared once and moved by the client: a rotation turns toward where it was sent at its
 * rate, a keyed map dot glides to its new place, and a display spins, picked up as far round as it
 * has got by a player who comes into view of it part way through, and dropped when stopped.
 */
public final class DeclaredMotion implements FabricClientGameTest {
	private static final String OVERLAY = "pandorical-gametest:motion";
	private static final float RATE = 10F;
	private static final int GLIDE = 10;
	private static final float SPIN_RATE = 0.1F;
	private static final int ARRIVAL = 1200;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!Smoke.full()) return;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			server.waitFor(s -> PandoricalApi.isAvailable(connection.getServerPlayer()));
			check(server.computeOnServer(s -> PandoricalApi.hasCapability(connection.getServerPlayer(), Capabilities.DECLARED_MOTION)),
				"the client does not declare declared_motion");

			turnsAndGlides(context, server, connection);
			spins(context, server, connection);
		}
	}

	private static void turnsAndGlides(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
		server.runOnServer(s -> PandoricalApi.hud().show(connection.getServerPlayer(), new HudBuilder(OVERLAY)
			.anchor("top_left")
			.component(new ComponentBuilder("wheel", ComponentType.SPRITE).bounds(0, 0, 16, 16)
				.rotation(0F).prop(ComponentType.PROP_TURN_RATE, RATE))
			.component(new ComponentBuilder("map", ComponentType.MAP).bounds(0, 20, 64, 64)
				.prop(ComponentType.PROP_INTERP_TICKS, GLIDE)
				.prop(ComponentType.PROP_MAP_DOTS, "0,0,-1,minecraft:zombie;20,20,-1,minecraft:cow")
				.prop(ComponentType.PROP_MAP_DOT_KEYS, "a,"))
			.build()));
		context.waitFor(client -> HudManager.getActiveOverlays().containsKey(OVERLAY));

		server.runOnServer(s -> PandoricalApi.hud().update(connection.getServerPlayer(), OVERLAY, List.of(
			new ComponentUpdateBuilder("wheel").rotation(100F).build(),
			new ComponentUpdateBuilder("map")
				.prop(ComponentType.PROP_MAP_DOTS, "40,0,-1,minecraft:zombie;30,30,-1,minecraft:cow")
				.prop(ComponentType.PROP_MAP_DOT_KEYS, "a,").build())));

		context.waitFor(client -> wheel() > 0F);
		check(context.computeOnClient(client -> unkeyedAt(30, 30)), "a dot with no key glided instead of being drawn where it is");
		context.waitTicks(3);
		float turned = context.computeOnClient(client -> wheel());
		check(turned > 0F && turned < 100F, "the rotation did not turn at its rate: " + turned + " degrees three ticks in");
		float glided = context.computeOnClient(client -> dot()[0]);
		check(glided > 0F && glided < 40F, "the keyed dot did not glide: at " + glided + " three ticks in");

		context.waitTicks(GLIDE + 2);
		check(context.computeOnClient(client -> wheel()) == 100F, "the rotation did not stop where it was sent");
		check(context.computeOnClient(client -> dot()[0]) == 40F, "the keyed dot did not arrive where it was sent");

		server.runOnServer(s -> PandoricalApi.hud().hide(connection.getServerPlayer(), OVERLAY));
	}

	private static void spins(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
		server.runCommand("gamemode spectator @a");
		BlockPos home = server.computeOnServer(s -> connection.getServerPlayer().blockPosition());
		BlockPos far = home.offset(2048, 0, 2048);
		server.runCommand("forceload add " + far.getX() + " " + far.getZ());
		int id = server.computeOnServer(s -> {
			s.overworld().getChunk(far);
			Display.BlockDisplay display = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, s.overworld());
			display.setPos(far.getX(), far.getY(), far.getZ());
			s.overworld().addFreshEntity(display);
			PandoricalApi.displaySpins().spin(display, new Vector3f(0.5F), new Vector3f(0, 1, 0), SPIN_RATE, 0);
			return display.getId();
		});
		long spunAt = System.currentTimeMillis();
		try {
			context.waitTicks(20);
			check(Float.isNaN(context.computeOnClient(client -> ClientDisplaySpins.angle(id))), "a spin two thousand blocks off was sent");

			tp(server, far);
			context.waitFor(client -> !Float.isNaN(ClientDisplaySpins.angle(id)), ARRIVAL);
			float angle = context.computeOnClient(client -> ClientDisplaySpins.angle(id));
			float expected = (System.currentTimeMillis() - spunAt) / 50F * SPIN_RATE % 360F;
			check(Math.abs(angle - expected) < 1F, "the spin started over on arrival: " + angle + " degrees, not " + expected);
			context.waitTicks(20);
			check(context.computeOnClient(client -> ClientDisplaySpins.angle(id)) > angle, "the display did not keep turning");

			server.runOnServer(s -> PandoricalApi.displaySpins().stop((Display) s.overworld().getEntity(id)));
			context.waitFor(client -> Float.isNaN(ClientDisplaySpins.angle(id)));

			// Spun again and then gone: forgotten with the entity.
			server.runOnServer(s -> PandoricalApi.displaySpins().spin((Display) s.overworld().getEntity(id),
				new Vector3f(0.5F), new Vector3f(1, 0, 0), 20F, 18));
			context.waitFor(client -> !Float.isNaN(ClientDisplaySpins.angle(id)));
			server.runOnServer(s -> s.overworld().getEntity(id).discard());
			context.waitFor(client -> Float.isNaN(ClientDisplaySpins.angle(id)));
		} finally {
			server.runOnServer(s -> {
				var display = s.overworld().getEntity(id);
				if (display != null) display.discard();
			});
			server.runCommand("forceload remove " + far.getX() + " " + far.getZ());
			tp(server, home);
		}
	}

	private static float wheel() {
		return ((AbstractComponent) overlay().componentIndex.get("wheel")).interpolatedGeometry(0F).rotation();
	}

	private static float[] dot() {
		return ((MapComponent) overlay().componentIndex.get("map")).drawnDot("a", 0F);
	}

	private static boolean unkeyedAt(int x, int z) {
		float[] at = ((MapComponent) overlay().componentIndex.get("map")).drawnDot("", 0F);
		return at != null && at[0] == x && at[1] == z;
	}

	private static HudOverlay overlay() {
		return HudManager.getActiveOverlays().get(OVERLAY);
	}

	private static void tp(TestServerContext server, BlockPos to) {
		server.runCommand("tp @a " + to.getX() + " " + to.getY() + " " + to.getZ());
	}

	private static void check(boolean holds, String otherwise) {
		if (!holds) throw new AssertionError(otherwise);
	}
}
