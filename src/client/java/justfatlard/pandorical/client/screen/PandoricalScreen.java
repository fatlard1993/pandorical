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
public class PandoricalScreen extends Screen implements justfatlard.pandorical.api.NavigableScreen {
    private final OpenScreenS2C screenDef;
    private final List<PandoricalComponent> components = new ArrayList<>();
    private final Map<String, PandoricalComponent> componentIndex = new HashMap<>();
    private final UpdateMemory updateMemory = new UpdateMemory();

    /** Chat without leaving the screen; see {@link ScreenChatBar} for the ordering contract. */
    private final ScreenChatBar chatBar = new ScreenChatBar();

    public PandoricalScreen(OpenScreenS2C screenDef) {
        super(Component.literal(screenDef.title()));
        this.screenDef = screenDef;
    }

    @Override
    protected void init() {
        super.init();
        // A resize rebuilds the tree; the old one is let go of properly, not just dropped
        components.forEach(ScreenHelper::removedTree);
        Map<String, PandoricalComponent> previous = new HashMap<>(componentIndex);
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
        updateMemory.restore(componentIndex, previous);
    }

    @Override
    public void tick() {
        super.tick();
        chatBar.tick();
        for (PandoricalComponent component : components) {
            ScreenHelper.tickTree(component);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // Let super handle blur/background (blur can only fire once per frame in 26.3+)
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        for (PandoricalComponent component : components) {
            ScreenHelper.renderComponentTree(component, graphics, mouseX, mouseY, delta);
        }
        chatBar.render(this, graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
        if (ScreenHelper.dispatchMouseClick(components, click.x(), click.y(), click.button())) {
            return true;
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (ScreenHelper.dispatchMouseReleased(components, click.x(), click.y(), click.button())) {
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // A key being bound is spent on the binding. It comes first because the key most worth
        // binding is one that already does something on the screen it is pressed on.
        if (justfatlard.pandorical.client.keybind.KeybindManager.captureKey(event)) {
            return true;
        }
        // An open chat bar owns the keyboard; the chat key only opens it once no
        // component (a focused text field) has claimed the key for itself
        if (chatBar.keyPressed(event)) {
            return true;
        }
        if (ScreenHelper.dispatchKeyPressed(components, event.key(), event.keycode(), event.modifiers())) {
            return true;
        }
        if (chatBar.tryOpen(event)) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (chatBar.charTyped(event)) {
            return true;
        }
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

    @Override
    public void removed() {
        super.removed();
        components.forEach(ScreenHelper::removedTree);
    }

    public void applyUpdates(List<ComponentUpdate> updates) {
        updateMemory.record(updates);
        ScreenHelper.applyUpdates(updates, componentIndex);
    }

    public String getScreenId() {
        return screenDef.screenId();
    }

    public String getScreenType() {
        return screenDef.screenType();
    }

    @Override
    public List<NavRegion> navRegions() {
        return ScreenHelper.navRegions(components);
    }

    private void sendAction(String componentId, Map<String, String> data) {
        ScreenHelper.sendAction(screenDef.screenId(), componentId, data);
    }
}
