package justfatlard.pandorical.form;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.api.ClientKind;
import justfatlard.pandorical.login.ClientKinds;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * The forms each player has open, so an answer can be matched to the form that asked. Vanilla
 * hands a dialog's answer to nobody in particular; {@code CustomClickActionMixin} brings it here
 * with the player who sent it.
 */
public final class Forms {
	static final String BUTTON_KEY = "button";
	private static final String NAMESPACE = "pandorical";
	private static final String PREFIX = "form/";
	/** Closing a dialog sends nothing back, so the oldest unanswered forms are let go past this. */
	private static final int OPEN_PER_PLAYER = 8;

	private static final AtomicLong NEXT = new AtomicLong();
	/**
	 * Mixed into every id, because any rich text with a custom click (a book, a styled chat line)
	 * can send one: a guessable id would let it answer somebody else's open form.
	 */
	private static final SecureRandom TOKENS = new SecureRandom();
	private static final Map<UUID, LinkedHashMap<Identifier, SimpleForm>> OPEN = new ConcurrentHashMap<>();
	private static final Map<Identifier, Consumer<ServerPlayer>> FIXED = new ConcurrentHashMap<>();

	private Forms() {}

	/**
	 * Whether this player gets forms where a Pandorical client gets screens. Decided by what
	 * joined, which is known from configuration on: a Pandorical client is still mid-handshake,
	 * without its screen capability yet, for a moment after joining, and mustn't get forms then.
	 */
	public static boolean wanted(ServerPlayer player) {
		return ClientKinds.of(player.getUUID()) != ClientKind.PANDORICAL;
	}

	public static void register() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> OPEN.remove(handler.getPlayer().getUUID()));
	}

	/** A click with this id, from a dialog shipped as data, runs {@code action} for whoever sent it. */
	public static void onClick(Identifier id, Consumer<ServerPlayer> action) {
		FIXED.put(id, action);
	}

	static Identifier remember(ServerPlayer player, SimpleForm form) {
		Identifier id = Identifier.fromNamespaceAndPath(NAMESPACE,
			PREFIX + NEXT.incrementAndGet() + "_" + Long.toHexString(TOKENS.nextLong() & Long.MAX_VALUE));
		// A handler can open a form as its player leaves; keeping it would outlive the disconnect.
		if (player.hasDisconnected()) return id;
		LinkedHashMap<Identifier, SimpleForm> open = OPEN.computeIfAbsent(player.getUUID(), unused -> new LinkedHashMap<>());
		synchronized (open) {
			open.put(id, form);
			while (open.size() > OPEN_PER_PLAYER) open.remove(open.keySet().iterator().next());
		}
		return id;
	}

	/** The form most recently shown to this player and still unanswered. */
	public static Optional<Identifier> newest(ServerPlayer player) {
		LinkedHashMap<Identifier, SimpleForm> open = OPEN.get(player.getUUID());
		if (open == null) return Optional.empty();
		synchronized (open) {
			return open.sequencedKeySet().reversed().stream().findFirst();
		}
	}

	/**
	 * Whether this click was one of Pandorical's forms. A form answers once; a click for a form this
	 * player wasn't sent, or one that doesn't match what it offered, is dropped and logged.
	 */
	public static boolean handle(ServerPlayer player, Identifier id, Optional<Tag> payload) {
		Consumer<ServerPlayer> fixed = FIXED.get(id);
		if (fixed != null) {
			fixed.accept(player);
			return true;
		}
		if (!id.getNamespace().equals(NAMESPACE) || !id.getPath().startsWith(PREFIX)) return false;

		LinkedHashMap<Identifier, SimpleForm> open = OPEN.get(player.getUUID());
		SimpleForm form;
		if (open == null) {
			form = null;
		} else {
			synchronized (open) {
				form = open.remove(id);
			}
		}
		if (form == null) {
			Pandorical.LOGGER.warn("[pandorical] {} answered a form they don't have open: {}", player.getName().getString(), id);
			return true;
		}

		Optional<SimpleForm.Answer> answer = form.read(player, payload);
		if (answer.isEmpty()) {
			Pandorical.LOGGER.warn("[pandorical] {} sent an answer the form {} didn't offer", player.getName().getString(), id);
			return true;
		}
		try {
			form.answer(answer.get());
		} catch (RuntimeException e) {
			Pandorical.LOGGER.error("[pandorical] A form's answer handler threw", e);
		}
		return true;
	}
}
