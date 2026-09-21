package justfatlard.pandorical.api;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;

/**
 * Things a player is being asked, kept out of the chat they are talking in.
 *
 * <p>A teleport request, an invitation to a fight, an offer to trade, a neighbour asking to grow
 * their geode into yours: each of these is a question with answers, and chat is a poor place to put
 * one. It scrolls away mid-conversation, it cannot be answered twice, and the answer is a coloured
 * word you have to notice and hit. Worse, on a busy evening the questions and the conversation are
 * the same column of text, so the chat everybody actually wants is the thing being buried.
 *
 * <p>So a question goes here instead. The player gets a quiet badge saying how many are waiting,
 * opens them on a key, and answers with a button. Chat stays chat.
 *
 * <p><b>This is not a toast.</b> A notice waits until it is answered, withdrawn, or runs out of
 * time - it is a question, and a question nobody answered is still a question. What it is not is a
 * way to say something: for telling a player a thing has happened and wanting nothing back, the
 * action bar is right there.
 *
 * <p>Built on the screens, HUD and keybinds this mod already carries rather than a wire format of
 * its own, so any client that can see a Pandorical screen can answer a notice, with nothing new to
 * install.
 */
public interface NoticeApi {

	/**
	 * One answer to a question, drawn as a button.
	 *
	 * @param id    what comes back to the handler
	 * @param icon  an item id, the way action menu buttons take one - {@code minecraft:lime_dye}
	 * @param label what the button reads
	 */
	record Choice(String id, String icon, String label) {}

	/**
	 * A question waiting for a player.
	 *
	 * @param id       this question's name, <b>per player and per kind</b>: offering again under an
	 *                 id already waiting replaces it rather than stacking a second copy. Two
	 *                 teleport requests from the same person are one question, not two, which is
	 *                 what stops somebody spamming a tray full of them
	 * @param kind     which handler answers it, and what withdrawAll takes to clear a group
	 * @param icon     an item id for the badge and the row
	 * @param summary  one line, read without opening anything: say who is asking and for what
	 * @param choices  the buttons, in the order they should read. An empty list is a notice with
	 *                 nothing to answer - allowed, and dismissed rather than answered
	 * @param seconds  how long it stands before it withdraws itself; zero or less to wait forever.
	 *                 An invitation that outlives the thing it invites you to is worse than none
	 */
	record Notice(String id, String kind, String icon, String summary, List<Choice> choices, int seconds) {
		public Notice {
			choices = List.copyOf(choices);
		}
	}

	/**
	 * Ask this player this question.
	 *
	 * <p>Replaces any notice this player already has under the same id and kind.
	 */
	void offer(ServerPlayer player, Notice notice);

	/**
	 * Take the question back, because it no longer means anything: the asker logged off, the fight
	 * started without them, the chest was broken.
	 *
	 * <p>Silent when there was nothing there, so a mod withdrawing on the way out never has to ask
	 * first.
	 */
	void withdraw(ServerPlayer player, String kind, String id);

	/** Every notice of this kind, for a mod tidying up after itself. */
	void withdrawAll(ServerPlayer player, String kind);

	/**
	 * What to do when a player answers one of this kind.
	 *
	 * <p>Called with the notice's own id and the chosen {@link Choice#id()}. The notice is gone by
	 * then: answering is what takes it away, so a handler that wants it back must offer it again.
	 *
	 * <p>Registered once, at mod init, the way screen actions are.
	 */
	void onChoice(String kind, ChoiceHandler handler);

	/** What a mod is told when its question is answered. */
	@FunctionalInterface
	interface ChoiceHandler {
		void answered(ServerPlayer player, String noticeId, String choiceId);
	}

	/**
	 * What to do when a notice of this kind runs out of time instead of being answered.
	 *
	 * <p>Optional. Without it, a notice that expires just goes - which is right for an invitation
	 * and wrong for anything holding something back waiting for an answer.
	 */
	void onExpiry(String kind, ExpiryHandler handler);

	/** What a mod is told when its question is given up on. */
	@FunctionalInterface
	interface ExpiryHandler {
		void expired(ServerPlayer player, String noticeId);
	}

	/**
	 * Put the tray in front of this player now.
	 *
	 * <p>The key opens it; so can a button on an action menu, or a mod that has just asked something
	 * it knows the player is waiting on.
	 */
	void open(ServerPlayer player);

	/** How many are waiting for this player, for a mod that wants to know before adding another. */
	int waiting(ServerPlayer player);
}
