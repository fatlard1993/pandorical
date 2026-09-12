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

/**
 * The declarative components on a Pandorical screen and its chat bar, owned by
 * {@link PandoricalScreen} and {@link PandoricalContainerScreen} alike, which extend different
 * vanilla screens and so cannot share a parent.
 *
 * <p>Each input method answers whether the event was spent here. One that was not goes on to the
 * screen's own super, and that is where the two screens differ: a plain screen's widgets, or a
 * container's slots and the container habits.
 */
final class ScreenComponents {
    private final List<PandoricalComponent> components = new ArrayList<>();
    private final Map<String, PandoricalComponent> componentIndex = new HashMap<>();
    private final UpdateMemory updateMemory = new UpdateMemory();

    /** Chat without leaving the screen; see {@link ScreenChatBar} for the ordering contract. */
    private final ScreenChatBar chatBar = new ScreenChatBar();

    /**
     * Build the tree afresh, as {@code init} does on open and on every resize. The old tree is
     * let go of properly, not just dropped, and the new one is brought up to date from it.
     */
    void rebuild(List<ComponentDef> defs, ComponentContext context, int originX, int originY) {
        Map<String, PandoricalComponent> previous = new HashMap<>(componentIndex);
        clear();
        for (ComponentDef def : defs) {
            components.add(ScreenHelper.buildComponent(def, context, originX, originY, componentIndex));
        }
        updateMemory.restore(componentIndex, previous);
    }

    /** Let the tree go and build nothing in its place. */
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
        // A key being bound is spent on the binding. It comes first because the key most worth
        // binding is one that already does something on the screen it is pressed on.
        if (KeybindManager.captureKey(event)) {
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

    /** The screen has gone: every tree hears it. */
    void removed() {
        components.forEach(ScreenHelper::removedTree);
    }

    List<NavRegion> navRegions() {
        return ScreenHelper.navRegions(components);
    }
}
