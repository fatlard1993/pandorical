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
 * <p>Kept per server id and per address the player reached the server at, so what one server
 * leaves is never shown to another, even one that names this server's id. A player who reaches
 * the same server by a second address starts that address with nothing kept. Read back during
 * login, so it is there by the time the player joins: {@link #get} answers from the first tick
 * they are in the world.
 *
 * <p>A game without Pandorical, or with one too old to keep anything, has nothing to give back:
 * {@link #canKeep} is false for it, {@link #get} answers null, and {@link #put} does nothing.
 *
 * <p>Only a value the server chose at random and kept to itself says anything about identity;
 * a value the player could have typed says nothing.
 */
public interface KeepsakeApi {
	/** Keys longer than this are refused. */
	int LONGEST_KEY = 64;
	/** Values longer than this are refused. */
	int LONGEST_VALUE = 256;
	/** A game keeps at most this many keys per server and address; a put beyond it is dropped by the game. */
	int MOST_KEYS = 64;

	/** Whether this player's game keeps values at all. */
	boolean canKeep(ServerPlayer player);

	/** What this player's game handed back for the key at login, or null. */
	String get(ServerPlayer player, String key);

	/**
	 * Leave a value with this player's game, kept until replaced. An empty value removes the key.
	 *
	 * @throws IllegalArgumentException if the key or value is longer than {@link #LONGEST_KEY} or {@link #LONGEST_VALUE}
	 */
	void put(ServerPlayer player, String key, String value);

	/** Take a value back off this player's game. */
	void remove(ServerPlayer player, String key);
}
