package justfatlard.pandorical.api;

import net.minecraft.server.level.ServerPlayer;

/**
 * A player stepping out of one mod's world into another's for a while, meaning to come back. Both
 * mods may hold the player's own things aside (an arena's kit, a builder's materials), and neither
 * may hand anything back while the other is still holding: the world they stepped out of waits for
 * them, and calls them back before it ends.
 *
 * <p>The world stepped out of says when it allows that with {@link #permit}; the mod stepped into
 * asks {@link #mayStepAside} before taking anyone in from a world whose mod holds things aside, and
 * says who it holds with {@link #host}. The world stepped out of asks {@link #away} before treating
 * a player gone from it as having left, and {@link #callBack} before ending their time there.
 */
public interface SideTripApi {
	/** The mod a player steps into. */
	interface Host {
		/** Whether this player is with this host now. Asked often: answer cheaply. */
		boolean holds(ServerPlayer player);

		/**
		 * End it now: what they carried in handed back, and set down where they stepped out from,
		 * before this returns.
		 */
		void sendBack(ServerPlayer player);
	}

	/** The mod whose world a player steps out of, saying whether this player may, from where they stand. */
	@FunctionalInterface
	interface Permit {
		boolean lets(ServerPlayer player);
	}

	void host(Host host);

	void permit(Permit permit);

	/** Whether any world this player is in lets them step aside from it. */
	boolean mayStepAside(ServerPlayer player);

	/** Whether this player is away with a host now. */
	boolean away(ServerPlayer player);

	/** Every host holding this player sends them back, at once. */
	void callBack(ServerPlayer player);
}
