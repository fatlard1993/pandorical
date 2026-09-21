package justfatlard.pandorical;

import justfatlard.pandorical.api.PandoricalApi;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What Pandorical says to somebody on the way in, held back until they are actually in.
 *
 * <p>Joining a server and being free to play are not the same moment. A mod can hold a player at
 * the spawn until they have said a password, and everything Pandorical has to offer a newcomer -
 * what the server runs, what changed since last time, which key opens the menus - is addressed to
 * somebody who has arrived. Put to somebody still in a pen it is noise over the one instruction
 * they need, and the list of everything installed here is not a thing to hand out before the
 * password.
 *
 * <p>So the offers wait. Held players are kept here and served the moment nothing says they are
 * held any longer, which is the same arrival a second late rather than one that never happens.
 *
 * <p>Whether anybody is held is asked of {@link PandoricalApi#heldWhile}, because the mod doing
 * the holding is the one that knows, and it is a mod built on Pandorical rather than the other
 * way round.
 */
public final class Arrivals {
	private Arrivals() {}

	/** Players who joined while something was holding them, waiting to be let in. */
	private static final Set<UUID> waiting = ConcurrentHashMap.newKeySet();

	/** Once a second is soon enough for something a player is waiting on a password for. */
	private static final int EVERY = 20;

	/** Somebody joined: served now, or when whatever is holding them lets go. */
	public static void arrived(ServerPlayer player) {
		if (PandoricalApi.isHeld(player)) {
			waiting.add(player.getUUID());
			return;
		}
		serve(player);
	}

	/** Anyone who was held and no longer is. */
	public static void tick(MinecraftServer server) {
		if (waiting.isEmpty() || server.getTickCount() % EVERY != 0) return;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!waiting.contains(player.getUUID())) continue;
			if (PandoricalApi.isHeld(player)) continue;
			waiting.remove(player.getUUID());
			serve(player);
		}
	}

	/** Left before being let in; the next visit works it out again. */
	public static void forget(ServerPlayer player) {
		waiting.remove(player.getUUID());
	}

	private static void serve(ServerPlayer player) {
		// The menus first: they are a feature rather than a message, and the brief mentions the
		// key that opens them.
		justfatlard.pandorical.actions.ActionMenuRegistry.INSTANCE.offerTo(player);
		justfatlard.pandorical.brief.Brief.offerTo(player);
		justfatlard.pandorical.changelog.Changelog.offerTo(player);
	}
}
