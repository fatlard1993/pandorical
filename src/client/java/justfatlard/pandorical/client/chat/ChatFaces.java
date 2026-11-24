package justfatlard.pandorical.client.chat;

import com.mojang.authlib.GameProfile;
import justfatlard.pandorical.client.component.PlayerFaceComponent;
import justfatlard.pandorical.client.settings.ClientPrefs;
import justfatlard.pandorical.client.settings.ClientSettings;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The sender's face beside each chat message a player sent. A message keeps its sender from the
 * moment vanilla shows it; its lines are drawn indented, and its first line carries the face. A
 * server's own messages, the Discord bridge's included, have no sender and no face.
 */
public final class ChatFaces {
	private ChatFaces() {}

	private static final String KEY = "chat_faces";
	private static final String SPACER = "   ";
	private static final int FACE = 8;

	private static Boolean wanted;

	/** The player whose message vanilla is showing now, between hearing it and adding it to chat. */
	private static @Nullable UUID showing;

	private static final Map<GuiMessage, UUID> senders = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<GuiMessage, FormattedCharSequence> firstLines = Collections.synchronizedMap(new WeakHashMap<>());

	public static void register() {
		ClientSettings.INSTANCE.group("pandorical", "Pandorical")
			.toggle(KEY, "Faces in chat",
				chatHeadsInstalled()
					? "Left to Chat Heads, which is installed"
					: "The sender's face beside each message a player sends",
				ChatFaces::wanted, ChatFaces::want);
	}

	private static boolean chatHeadsInstalled() {
		return FabricLoader.getInstance().isModLoaded("chat_heads");
	}

	private static boolean wanted() {
		if (wanted == null) wanted = ClientPrefs.getBoolean(KEY, true);
		return wanted;
	}

	private static void want(boolean on) {
		wanted = on;
		ClientPrefs.set(KEY, on);
		Minecraft.getInstance().gui.hud.getChat().rescaleChat();
	}

	public static boolean active() {
		return wanted() && !chatHeadsInstalled();
	}

	public static void showing(@Nullable GameProfile sender) {
		showing = sender == null ? null : sender.id();
	}

	/** For each message chat makes: whose it is, if a player's message is being shown. */
	public static GuiMessage made(GuiMessage message) {
		UUID sender = showing;
		if (sender != null) senders.put(message, sender);
		return message;
	}

	/** The newest message with a sender whose text contains {@code text}, for tests. */
	public static Optional<GuiMessage> made(String text) {
		synchronized (senders) {
			return senders.keySet().stream().filter(m -> m.content().getString().contains(text))
				.max(Comparator.comparingInt(GuiMessage::addedTime));
		}
	}

	public static @Nullable UUID senderOf(GuiMessage message) {
		return active() ? senders.get(message) : null;
	}

	/** Narrower, so a message with a face still wraps inside the chat box. */
	public static int wrapWidth(GuiMessage message, int width) {
		return senderOf(message) == null ? width : width - Minecraft.getInstance().font.width(SPACER);
	}

	/** The line of a message chat drew first, which carries the face; null for a message without one. */
	public static @Nullable FormattedCharSequence firstLineOf(GuiMessage message) {
		return firstLines.get(message);
	}

	public static void split(GuiMessage message, List<FormattedCharSequence> lines) {
		if (!lines.isEmpty() && senderOf(message) != null) firstLines.put(message, lines.get(0));
	}

	/**
	 * One line about to be drawn: indented if its message has a sender, with the face drawn into
	 * the indent on the message's first line when {@code graphics} draws rather than only measures.
	 */
	public static FormattedCharSequence line(GuiMessage.Line line, int y, float opacity, @Nullable GuiGraphicsExtractor graphics) {
		UUID sender = senderOf(line.parent());
		if (sender == null) return line.content();
		if (graphics != null && firstLines.get(line.parent()) == line.content()) {
			var skin = PlayerFaceComponent.skinOf(Minecraft.getInstance(), sender);
			PlayerFaceExtractor.extractRenderState(graphics, skin, 0, y, FACE, ARGB.white(opacity));
		}
		return FormattedCharSequence.composite(FormattedCharSequence.forward(SPACER, Style.EMPTY), line.content());
	}
}
