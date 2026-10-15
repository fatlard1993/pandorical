package justfatlard.pandorical.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.PauseScreen;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A client mod the server sent is offered from the pause menu, in game.
 *
 * <p>The house launcher joins the server on start, so its players never see the title screen or
 * the server list, which were the only places that asked: every update was downloaded on every
 * join and installed never. This stages one and opens the pause menu in a world.
 */
public final class UpdatesAskInGame implements FabricClientGameTest {

	private static final String ID = "pandorical-staged-probe";

	@Override
	public void runTest(ClientGameTestContext context) {
		Path stage = FabricLoader.getInstance().getGameDir().resolve("pandorical-update");
		Path jar = stage.resolve(ID + "@1.0.0.jar");
		Path declined = FabricLoader.getInstance().getConfigDir().resolve("pandorical").resolve("client-mods-declined.properties");
		try {
			Files.createDirectories(stage);
			writeJar(jar);
		} catch (IOException e) {
			throw new AssertionError("could not stage the probe", e);
		}

		try {
			Smoke.run(context, "pandorical", session -> {
				context.setScreen(() -> new PauseScreen(true));
				context.waitTicks(5);
				String seen = context.computeOnClient(client -> client.gui.screen() == null ? "nothing"
					: client.gui.screen().getClass().getSimpleName());
				if (!seen.equals(ConfirmScreen.class.getSimpleName())) {
					throw new AssertionError("the pause menu did not offer the staged update; the screen is " + seen);
				}
				// Left unanswered: the probe is never installed, and is deleted below.
				context.setScreen(() -> null);
			});
		} finally {
			try {
				Files.deleteIfExists(jar);
				Files.deleteIfExists(declined);
			} catch (IOException ignored) {
				// A probe left behind is cleared as useless by the next run's own check.
			}
		}
	}

	private static void writeJar(Path jar) throws IOException {
		try (OutputStream out = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(out)) {
			zip.putNextEntry(new ZipEntry("fabric.mod.json"));
			zip.write(("{\"schemaVersion\":1,\"id\":\"" + ID + "\",\"version\":\"1.0.0\"}").getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}
	}
}
