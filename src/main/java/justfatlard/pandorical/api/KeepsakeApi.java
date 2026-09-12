package justfatlard.pandorical.api;

import net.minecraft.server.level.ServerPlayer;

/**
 * Small values a server leaves with a player's own game, and gets back each time they join.
 *
 * <p>Kept per server id and per address the player reached the server at, so no other server sees
 * them, even one claiming this server's id, and a second address starts with nothing. Read back
 * during login: {@link #get} answers from the player's first tick in the world.
 *
 * <p>A game without Pandorical, or with one too old to keep anything: {@link #canKeep} is false,
 * {@link #get} answers null, and {@link #put} does nothing.
 *
 * <p>Only a value the server chose at random and kept to itself says anything about identity; a
 * value the player could have typed says nothing.
 */
public interface KeepsakeApi {
	int LONGEST_KEY = 64;
	int LONGEST_VALUE = 256;
	/** A game keeps at most this many keys per server and address; a put beyond it is dropped by the game. */
	int MOST_KEYS = 64;

	boolean canKeep(ServerPlayer player);

	/** What the game handed back for the key at login, or null. */
	String get(ServerPlayer player, String key);

	/**
	 * Kept until replaced. An empty value removes the key.
	 *
	 * @throws IllegalArgumentException if the key or value is longer than {@link #LONGEST_KEY} or {@link #LONGEST_VALUE}
	 */
	void put(ServerPlayer player, String key, String value);

	void remove(ServerPlayer player, String key);
}
