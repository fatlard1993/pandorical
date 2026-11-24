package justfatlard.pandorical.client.chat;

import justfatlard.pandorical.client.mixin.ChatDrawingAccessor;
import justfatlard.pandorical.client.mixin.ChatFocusedAccessor;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

/** The graphics behind one of chat's drawing accesses; null for the one that only measures clicks. */
public final class ChatGraphics {
	private ChatGraphics() {}

	public static @Nullable GuiGraphicsExtractor of(Object access) {
		if (access instanceof ChatDrawingAccessor drawing) return drawing.pandorical$graphics();
		if (access instanceof ChatFocusedAccessor focused) return focused.pandorical$graphics();
		return null;
	}
}
