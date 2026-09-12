package justfatlard.pandorical.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import justfatlard.pandorical.api.HudBuilder;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.ScreenBuilder;
import justfatlard.pandorical.client.hud.HudManager;
import justfatlard.pandorical.client.screen.PandoricalScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.util.Annotations;

/** Pandorical launches, applies every mixin, joins a world, and shows a screen and a HUD. */
public final class LaunchGate implements FabricClientGameTest {
	private static final List<String> MIXIN_CONFIGS = List.of("pandorical.mixins.json", "pandorical.client.mixins.json");
	private static final String OVERLAY_ID = "pandorical-gametest:overlay";

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> applyEveryMixin());

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();

			// Screens and HUDs are dropped until the client answers Pandorical's hello, and content
			// is ready only once GateContent's block has gone over in the configuration phase.
			server.waitFor(s -> PandoricalApi.isContentReady(connection.getServerPlayer()));

			String screenId = server.computeOnServer(s -> {
				ScreenBuilder screen = new ScreenBuilder("pandorical-gametest:gate").size(120, 64).title("Launch gate");
				screen.panel("bg", 0, 0, 120, 64, Map.of("border", "beveled"));
				screen.text("line", 8, 8, "It launched.");
				screen.button("ok", 8, 36, 50, 20, Map.of("label", "OK"));
				PandoricalApi.screens().open(connection.getServerPlayer(), screen.build());
				return screen.screenId();
			});
			context.waitForScreen(PandoricalScreen.class);
			server.runOnServer(s -> PandoricalApi.screens().close(connection.getServerPlayer(), screenId));
			context.waitForScreen(null);

			server.runOnServer(s -> PandoricalApi.hud().show(connection.getServerPlayer(),
				new HudBuilder(OVERLAY_ID).text("line", 0, 0, "It launched.").build()));
			context.waitFor(client -> HudManager.getActiveOverlays().containsKey(OVERLAY_ID));
			server.runOnServer(s -> PandoricalApi.hud().hide(connection.getServerPlayer(), OVERLAY_ID));
			context.waitFor(client -> !HudManager.getActiveOverlays().containsKey(OVERLAY_ID));
		}
	}

	/**
	 * Loads every class a Pandorical mixin targets and fails unless each mixin named in
	 * Pandorical's configs was applied, then loads every other mod's targets too. A target that
	 * fails to transform, a require miss included, throws as it loads; a mixin left out, or a
	 * target loaded before Mixin could reach it, shows up as never applied.
	 */
	private static void applyEveryMixin() {
		List<String> failures = new ArrayList<>();
		for (String mixin : declaredMixins()) {
			for (String target : targetsOf(mixin)) {
				try {
					Class.forName(target, false, LaunchGate.class.getClassLoader());
				} catch (Throwable t) {
					while (t.getCause() != null) t = t.getCause();
					failures.add(mixin + " on " + target + ": " + t);
				}
			}
			// Mixin records an application on the mixin's own ClassInfo, not on the target's.
			if (Mixins.getMixinsForClass(mixin).isEmpty()) failures.add(mixin + " was never applied");
		}
		if (!failures.isEmpty()) throw new AssertionError("Pandorical mixins failed:\n  " + String.join("\n  ", failures));

		MixinEnvironment.getCurrentEnvironment().audit();
	}

	/** From the mixin's own {@code @Mixin}: Mixin empties {@link Mixins#getConfigs()} as it selects each config. */
	private static List<String> targetsOf(String mixin) {
		ClassNode node;
		try {
			node = MixinService.getService().getBytecodeProvider().getClassNode(mixin.replace('.', '/'));
		} catch (ClassNotFoundException | IOException e) {
			throw new AssertionError("reading " + mixin, e);
		}
		AnnotationNode annotation = Annotations.getInvisible(node, Mixin.class);
		List<String> targets = new ArrayList<>();
		for (Type type : Annotations.<Type>getValue(annotation, "value", true)) targets.add(type.getClassName());
		for (String name : Annotations.<String>getValue(annotation, "targets", true)) targets.add(name.replace('/', '.'));
		return targets;
	}

	private static Set<String> declaredMixins() {
		Set<String> mixins = new TreeSet<>();
		for (String name : MIXIN_CONFIGS) {
			try (InputStream in = LaunchGate.class.getClassLoader().getResourceAsStream(name)) {
				if (in == null) throw new AssertionError(name + " is not on the classpath");
				JsonObject config = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				String pkg = config.get("package").getAsString();
				for (String side : List.of("mixins", "client", "server")) {
					if (config.has(side)) config.getAsJsonArray(side).forEach(m -> mixins.add(pkg + "." + m.getAsString()));
				}
			} catch (IOException e) {
				throw new AssertionError("reading " + name, e);
			}
		}
		return mixins;
	}
}
