package justfatlard.pandorical.client.update;

import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;

/**
 * The CurseForge build's stand-in: it takes no mods from servers. Registering nothing means the
 * client never answers a server's offer channel, so a server never sends one.
 */
public final class KeptClientMods {
	private KeptClientMods() {}

	public static void register() {}

	public static boolean holdDisconnect(Connection connection, Component reason) {
		return false;
	}
}
