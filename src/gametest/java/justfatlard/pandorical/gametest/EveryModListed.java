package justfatlard.pandorical.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import justfatlard.pandorical.settings.ClientMods;
import justfatlard.pandorical.settings.ModCatalog;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Every mod the client runs is declared to the server, including ones that never heard of
 * Pandorical.
 *
 * <p>Breaks quietly: client mods that also run on the server are listed from the server's own
 * catalog, so a lost declaration costs only the client-only mods - a short list nobody counts.
 */
public final class EveryModListed implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "pandorical", session -> {
			// What the client is actually running, by the same rules the declaration uses.
			List<String> here = context.computeOnClient(client -> {
				List<String> ids = new ArrayList<>();
				for (var container : FabricLoader.getInstance().getAllMods()) {
					String id = container.getMetadata().getId();
					if (ModCatalog.isPlumbing(id)) continue;
					// A mod inside another is the other mod, as far as a reader is concerned.
					if (container.getContainingMod().isPresent()) continue;
					ids.add(id);
				}
				return ids;
			});
			check(!here.isEmpty(), "the client reported running no mods at all");

			AtomicReference<String> missing = new AtomicReference<>();
			session.onServer(server -> {
				Set<String> declared = new java.util.HashSet<>();
				for (ModCatalog.ModInfo mod : ClientMods.of(session.player())) declared.add(mod.id());
				List<String> absent = new ArrayList<>();
				for (String id : here) {
					if (!declared.contains(id)) absent.add(id);
				}
				if (!absent.isEmpty()) {
					missing.set("the client is running " + absent + " and never told the server;"
						+ " it declared " + declared);
				}
			});
			check(missing.get() == null, missing.get());
		});
	}

	private static void check(boolean ok, String complaint) {
		if (!ok) throw new AssertionError(complaint);
	}
}
