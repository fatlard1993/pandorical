package justfatlard.pandorical.gametest;

import justfatlard.pandorical.client.chat.ChatFaces;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.multiplayer.chat.GuiMessage;

/** A player's chat message carries their face: tagged with them, indented, and wrapped to fit beside it. */
public final class ChatFacesShow implements FabricClientGameTest {
	private static final String SAID = "faces in chat are on";

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "pandorical", session -> {
			context.runOnClient(client -> client.getConnection().sendChat(SAID));
			session.waitTicks(20);
			String verdict = context.computeOnClient(client -> {
				GuiMessage message = ChatFaces.made(SAID).orElse(null);
				if (message == null) return "the message never came back tagged with its sender";
				if (!client.player.getUUID().equals(ChatFaces.senderOf(message))) return "the message is tagged with someone else";
				var first = ChatFaces.firstLineOf(message);
				if (first == null) return "chat never split the message into a line for the face";
				var line = new GuiMessage.Line(message, first, true);
				int plain = client.font.width(line.content());
				int drawn = client.font.width(ChatFaces.line(line, 0, 1.0F, null));
				if (drawn <= plain) return "the line was not indented for the face";
				if (ChatFaces.wrapWidth(message, 320) >= 320) return "the message wraps as wide as one without a face";
				return null;
			});
			if (verdict != null) throw new AssertionError(verdict);
			if (session.full()) {
				context.takeScreenshot("chat-faces");
			}
		});
	}
}
