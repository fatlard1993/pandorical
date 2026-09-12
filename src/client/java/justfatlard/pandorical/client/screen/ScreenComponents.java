package justfatlard.pandorical.client.screen;

import justfatlard.pandorical.api.NavigableScreen.NavRegion;
import justfatlard.pandorical.client.component.ComponentContext;
import justfatlard.pandorical.client.component.PandoricalComponent;
import justfatlard.pandorical.client.keybind.KeybindManager;
import justfatlard.pandorical.protocol.ComponentDef;
import justfatlard.pandorical.protocol.ComponentUpdate;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The components and chat bar of a PandoricalScreen or PandoricalContainerScreen. */
final class ScreenComponents {
    private final List<PandoricalComponent> components = new ArrayList<>();
    private final Map<String, PandoricalComponent> componentIndex = new HashMap<>();
    private final UpdateMemory updateMemory = new UpdateMemory();

    private final ScreenChatBar chatBar = new ScreenChatBar();

    /** On open and on every resize; the new tree takes over from the old. */
    void rebuild(List<ComponentDef> defs, ComponentContext context, int originX, int originY) {
        Map<String, PandoricalComponent> previous = new HashMap<>(componentIndex);
        clear();
        for (ComponentDef def : defs) {
            components.add(ScreenHelper.buildComponent(def, context, originX, originY, componentIndex));
        }
        updateMemory.restore(componentIndex, previous);
    }

    void clear() {
        components.forEach(ScreenHelper::removedTree);
        components.clear();
        componentIndex.clear();
    }

    void tick() {
        chatBar.tick();
        for (PandoricalComponent component : components) {
            ScreenHelper.tickTree(component);
        }
    }

    void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        for (PandoricalComponent component : components) {
            ScreenHelper.renderComponentTree(component, graphics, mouseX, mouseY, delta);
        }
    }

    void renderOverlays(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        for (PandoricalComponent component : components) {
            ScreenHelper.renderOverlayTree(component, graphics, mouseX, mouseY, delta);
        }
    }

    void renderChat(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        chatBar.render(screen, graphics, mouseX, mouseY, delta);
    }

    boolean mouseClicked(MouseButtonEvent click) {
        return ScreenHelper.dispatchMouseClick(components, click.x(), click.y(), click.button());
    }

    boolean mouseReleased(MouseButtonEvent click) {
        return ScreenHelper.dispatchMouseReleased(components, click.x(), click.y(), click.button());
    }

    boolean keyPressed(KeyEvent event) {
        // Keybind capture first: the key being bound may already do something on this screen.
        if (KeybindManager.captureKey(event)) {
            return true;
        }
        // An open bar owns the keyboard; the chat key opens it only if no component claimed it.
        if (chatBar.keyPressed(event)) {
            return true;
        }
        if (ScreenHelper.dispatchKeyPressed(components, event.key(), event.keycode(), event.modifiers())) {
            return true;
        }
        return chatBar.tryOpen(event);
    }

    boolean charTyped(CharacterEvent event) {
        if (chatBar.charTyped(event)) {
            return true;
        }
        return ScreenHelper.dispatchCharTyped(components, event.codepoint());
    }

    boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        return ScreenHelper.dispatchMouseScrolled(components, mouseX, mouseY, verticalAmount);
    }

    void applyUpdates(List<ComponentUpdate> updates) {
        updateMemory.record(updates);
        ScreenHelper.applyUpdates(updates, componentIndex);
    }

    void removed() {
        components.forEach(ScreenHelper::removedTree);
    }

    List<NavRegion> navRegions() {
        return ScreenHelper.navRegions(components);
    }
}
