package justfatlard.pandorical.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * Chat input inside a Pandorical screen, since vanilla's chat screen would close it, and closing a
 * container screen is its cancel action. An open bar takes every key; the mouse stays with the
 * screen. {@link ScreenComponents#keyPressed} orders it against the components.
 */
public final class ScreenChatBar {
    private static final int MAX_CHAT_LENGTH = 256;
    private static final int BAR_HEIGHT = 12;
    private static final int BAR_MARGIN = 2;
    private static final int TEXT_INSET = 2;
    private static final int BACKDROP_COLOR = 0x80000000;

    private EditBox input;
    /**
     * The opening key press is followed by its own character event. Cleared every tick, so a key
     * that types no character cannot swallow a later one.
     */
    private boolean swallowOpeningChar;

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

    public void tick() {
        this.swallowOpeningChar = false;
    }

    /** Draw last, over everything. */
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
