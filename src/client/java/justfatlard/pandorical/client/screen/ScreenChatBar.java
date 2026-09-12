package justfatlard.pandorical.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * Vanilla-style chat entry that lives INSIDE a Pandorical screen, so talking does not mean
 * leaving. A trade being haggled over, a mail screen mid-compose: these are exactly the moments a
 * player wants to say something, and vanilla's answer (chat is its own screen) would close the UI
 * they are talking about - for a container screen that is not just visual, closing is the cancel
 * action.
 *
 * <p>The vanilla chat key (and the command key, seeded with "/") opens a chat input bar along the
 * bottom of the window, drawn where vanilla's own chat input sits so it reads as chat at a glance.
 * Enter sends through the ordinary connection (commands included), Esc puts the bar away; either
 * way the screen underneath never moved. Incoming messages need nothing from us: the chat HUD
 * already renders under every screen.
 *
 * <p>While the bar is open it owns the KEYBOARD outright - inventory-close, navigation, every key
 * lands in the bar, matching vanilla chat's modality - but the mouse stays with the screen, so
 * slots and buttons keep working mid-sentence.
 *
 * <p>{@link ScreenComponents} is responsible for ordering: an open bar is offered keys before
 * anything else (modality), and {@link #tryOpen} runs only after component dispatch has declined
 * the key, so a focused text field keeps its letter T.
 */
public final class ScreenChatBar {
    private static final int MAX_CHAT_LENGTH = 256;
    private static final int BAR_HEIGHT = 12;
    private static final int BAR_MARGIN = 2;
    private static final int TEXT_INSET = 2;
    private static final int BACKDROP_COLOR = 0x80000000;

    private EditBox input;
    /**
     * The key press that opens the bar is followed by that key's own character event, which would
     * otherwise land in the box as its first letter (vanilla dodges this by opening ChatScreen
     * outside the event dispatch entirely). One swallowed character, cleared every tick so a
     * trigger key that produces no character cannot cost a real one later.
     */
    private boolean swallowOpeningChar;

    /** Open the bar when this is the chat or command key; true when the event was consumed. */
    public boolean tryOpen(KeyEvent event) {
        if (this.input != null) return false;

        Minecraft minecraft = Minecraft.getInstance();
        String seed;
        if (minecraft.options.keyChat.matches(event)) {
            seed = "";
        } else if (minecraft.options.keyCommand.matches(event)) {
            seed = "/";
        } else {
            return false;
        }

        this.input = new EditBox(minecraft.font, 0, 0, 10, BAR_HEIGHT,
            Component.translatable("chat.editBox"));
        this.input.setMaxLength(MAX_CHAT_LENGTH);
        this.input.setBordered(false);
        this.input.setValue(seed);
        this.input.moveCursorToEnd(false);
        this.input.setFocused(true);
        this.swallowOpeningChar = true;
        return true;
    }

    /** Keyboard while open: Enter sends, Esc dismisses, everything else is the box's. */
    public boolean keyPressed(KeyEvent event) {
        if (this.input == null) return false;

        int key = event.key();
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            send(this.input.getValue().trim());
            close();
            return true;
        }
        if (key == InputConstants.KEY_ESCAPE) {
            close();
            return true;
        }
        this.input.keyPressed(event);
        return true;
    }

    public boolean charTyped(CharacterEvent event) {
        if (this.input == null) return false;
        if (this.swallowOpeningChar) {
            this.swallowOpeningChar = false;
            return true;
        }
        this.input.charTyped(event);
        return true;
    }

    /** Call once per screen tick; the opening-char swallow must not outlive its own frame. */
    public void tick() {
        this.swallowOpeningChar = false;
    }

    /** Draw last, over everything, pinned to the window bottom where vanilla's chat input sits. */
    public void render(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (this.input == null) return;

        int y = screen.height - BAR_MARGIN - BAR_HEIGHT;
        this.input.setX(BAR_MARGIN + TEXT_INSET + 2);
        this.input.setY(y + TEXT_INSET);
        this.input.setWidth(screen.width - BAR_MARGIN * 2 - TEXT_INSET * 2 - 2);

        graphics.fill(BAR_MARGIN, y, screen.width - BAR_MARGIN, y + BAR_HEIGHT, BACKDROP_COLOR);
        this.input.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void send(String text) {
        if (text.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        if (text.startsWith("/")) {
            minecraft.player.connection.sendCommand(text.substring(1));
        } else {
            minecraft.player.connection.sendChat(text);
        }
    }

    private void close() {
        this.input = null;
        this.swallowOpeningChar = false;
    }
}
