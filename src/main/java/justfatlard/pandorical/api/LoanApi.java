package justfatlard.pandorical.api;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Whether what a player carries is theirs right now. Sometimes a mod puts a player's own things
 * aside and hands them a pack that is not theirs to give away: a builder's materials that are a
 * view of a shared supply, a loadout for an arena, a lesson's props. While it lasts, the player
 * is "on loan".
 *
 * <p>The mod that lends says so with {@link #register}. A mod that moves things into or out of a
 * player's pack some way the game itself does not (a trade, a parcel, a command that gives or
 * takes, a teleport that brings someone to them) asks {@link #onLoan} first, and refuses with the
 * answer: what goes out would be made from nothing, and what goes in would be lost when the loan
 * ends.
 */
public interface LoanApi {
	/** Says why this player's pack is on loan, or null when it is their own. */
	@FunctionalInterface
	interface Lender {
		@Nullable Component lent(ServerPlayer player);
	}

	void register(Lender lender);

	/**
	 * Why this player's pack is not theirs to trade, put to someone ("building at a Miniature
	 * Bench"), or null when it is their own. Asked often: lenders answer cheaply.
	 */
	@Nullable Component onLoan(ServerPlayer player);
}
