package justfatlard.pandorical.gametest;

import java.util.concurrent.atomic.AtomicInteger;
import justfatlard.pandorical.keybind.KeybindPool;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.locale.Language;

/**
 * A keybind a server names is named in the player's controls screen.
 *
 * <p>The pool ships as sixteen rows reading "Pandorical Action 1" through 16, because the client
 * has to register them before it has ever heard of a server. A player looking for the key they
 * press to open their notices finds that row by its name or does not find it at all, so the label
 * the server gave it has to arrive and win.
 *
 * <p>It arrives in the virtual pack's language file, which is a path the shipped jar also has:
 * proof that the two merge per key rather than one replacing the other, and that the server's
 * entry is the one on top. That is several moving parts to rest a screen on, and none of them
 * would say anything if they stopped.
 */
public final class KeybindsNamed implements FabricClientGameTest {

	/** Pandorical's own keybind, registered on every server, so this test needs no fixture mod. */
	private static final String NOTICES_KEYBIND = "pandorical:notices";

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "pandorical", session -> {
			// Pandorical's own, registered on every server: the notice tray's key.
			AtomicInteger found = new AtomicInteger(-1);
			session.onServer(server -> {
				for (KeybindPool.Claim claim : KeybindPool.INSTANCE.claims()) {
					if (NOTICES_KEYBIND.equals(claim.id())) found.set(claim.slot());
				}
			});
			int slot = found.get();
			check(slot >= 0, "the notices keybind claimed no slot, so there is nothing to name");

			String key = "key.pandorical.action" + (slot + 1);
			String shown = context.computeOnClient(client -> Language.getInstance().getOrDefault(key));

			check("Open notices".equals(shown),
				"the controls screen calls " + key + " \"" + shown + "\"; the server named it"
					+ " \"Open notices\". The row a player has to find is the one they cannot.");

			// The rest of the shipped file has to survive the override, or naming one key costs
			// every other string Pandorical ships.
			String untouched = context.computeOnClient(client ->
				Language.getInstance().getOrDefault("key.pandorical.menus"));
			check("Open action menus".equals(untouched),
				"overriding one keybind label took the rest of the language file with it:"
					+ " key.pandorical.menus now reads \"" + untouched + "\"");
		});
	}

	private static void check(boolean ok, String complaint) {
		if (!ok) throw new AssertionError(complaint);
	}
}
