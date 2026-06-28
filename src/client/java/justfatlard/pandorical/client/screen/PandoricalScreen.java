package justfatlard.pandorical.client.screen;

import justfatlard.pandorical.client.component.*;
import justfatlard.pandorical.protocol.ComponentDef;
import justfatlard.pandorical.protocol.ComponentUpdate;
import justfatlard.pandorical.protocol.OpenScreenS2C;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Non-container declarative screen. Used for dialogue, message detail,
 * recipe browsers, and other screens that don't need inventory slots.
 */
public class PandoricalScreen extends Screen {
    private final OpenScreenS2C screenDef;
    private final List<PandoricalComponent> components = new ArrayList<>();
    private final Map<String, PandoricalComponent> componentIndex = new HashMap<>();

    public PandoricalScreen(OpenScreenS2C screenDef) {
        super(Component.literal(screenDef.title()));
        this.screenDef = screenDef;
    }

    @Override
    protected void init() {
        super.init();
        components.clear();
        componentIndex.clear();

        int screenX = (this.width - screenDef.width()) / 2;
        int screenY = (this.height - screenDef.height()) / 2;

        ComponentContext context = new ComponentContext(
            screenDef.screenId(),
            screenDef.screenType(),
            screenX, screenY,
            this.font,
            this::sendAction,
            null
        );

        for (ComponentDef def : screenDef.components()) {
            PandoricalComponent component = ScreenHelper.buildComponent(def, context, screenX, screenY, componentIndex);
            components.add(component);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // Let super handle blur/background (blur can only fire once per frame in 26.3+)
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        for (PandoricalComponent component : components) {
            ScreenHelper.renderComponentTree(component, graphics, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean handled) {
        if (handled) return super.mouseClicked(click, handled);

        if (ScreenHelper.dispatchMouseClick(components, click.x(), click.y(), click.button())) {
            return true;
        }
        return super.mouseClicked(click, handled);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (ScreenHelper.dispatchKeyPressed(components, event.key(), event.scancode(), event.modifiers())) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (ScreenHelper.dispatchCharTyped(components, event.codepoint())) {
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (ScreenHelper.dispatchMouseScrolled(components, mouseX, mouseY, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean isPauseScreen() {
        return screenDef.pauseGame();
    }

    @Override
    public void onClose() {
        sendAction("_screen", Map.of());
        super.onClose();
    }

    public void applyUpdates(List<ComponentUpdate> updates) {
        ScreenHelper.applyUpdates(updates, componentIndex);
    }

    public String getScreenId() {
        return screenDef.screenId();
    }

    private void sendAction(String componentId, Map<String, String> data) {
        ScreenHelper.sendAction(screenDef.screenId(), componentId, data);
    }
}
