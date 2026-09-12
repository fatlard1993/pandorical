package justfatlard.pandorical.api;

import net.minecraft.server.level.ServerPlayer;

/**
 * Small values a server leaves with a player's own game, and gets back each time they join.
 *
 * <p>What a server knows about who is connecting is a name, which in offline mode anybody can
 * type, and an address, which changes whenever the player's provider hands out a new one - a
 * satellite link does it every few days. Something the game itself keeps, and hands back, is
 * the one thing that says "the same machine as last time" whatever the address is doing.
 *
 * <p>Kept per server, under an id the server makes for itself, so what one server leaves is
 * never shown to another. Read back during login, so it is there by the time the player joins:
 * {@link #get} answers from the first tick they are in the world.
 *
 * <p>A game without Pandorical, or with one too old to keep anything, simply has nothing to
 * give back; {@link #get} answers null for it and {@link #put} does nothing.
 */
public interface KeepsakeApi {
	/** What this player's game handed back for the key at login, or null. */
	String get(ServerPlayer player, String key);

	/** Leave a value with this player's game, kept until replaced. Short keys and values only. */
	void put(ServerPlayer player, String key, String value);

	/** Take a value back off this player's game. */
	void remove(ServerPlayer player, String key);
}
