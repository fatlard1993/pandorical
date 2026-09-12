package justfatlard.pandorical.client.screen;

import justfatlard.pandorical.client.component.*;
import justfatlard.pandorical.protocol.ComponentDef;
import justfatlard.pandorical.protocol.ComponentUpdate;
import justfatlard.pandorical.protocol.OpenScreenS2C;
import justfatlard.pandorical.screen.PandoricalMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Container screen with declarative UI + vanilla slot sync.
 * Used for screens that manage item slots (trade, backpack, etc.).
 */
public class PandoricalContainerScreen extends AbstractContainerScreen<PandoricalMenu> implements justfatlard.pandorical.api.NavigableScreen {
    private final OpenScreenS2C screenDef;
    private final List<PandoricalComponent> components = new ArrayList<>();
    private final Map<String, PandoricalComponent> componentIndex = new HashMap<>();
    private final UpdateMemory updateMemory = new UpdateMemory();

    /** Chat without leaving the screen; see {@link ScreenChatBar} for the ordering contract. */
    private final ScreenChatBar chatBar = new ScreenChatBar();

    public PandoricalContainerScreen(PandoricalMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title,
              menu.getScreenDef() != null ? menu.getScreenDef().width() : 176,
              menu.getScreenDef() != null ? menu.getScreenDef().height() : 166);
        this.screenDef = menu.getScreenDef();
        this.inventoryLabelY = 1000; // hide default labels
        this.titleLabelX = 1000;
    }

    @Override
    protected void init() {
        super.init();
        components.forEach(ScreenHelper::removedTree);
        Map<String, PandoricalComponent> previous = new HashMap<>(componentIndex);
        components.clear();
        componentIndex.clear();

        if (screenDef == null) return;

        ComponentContext context = new ComponentContext(
            screenDef.screenId(),
            screenDef.screenType(),
            this.leftPos, this.topPos,
            this.font,
            this::sendAction,
            this.menu
        );

        for (ComponentDef def : screenDef.components()) {
            PandoricalComponent component = ScreenHelper.buildComponent(def, context, this.leftPos, this.topPos, componentIndex);
            components.add(component);
        }
        updateMemory.restore(componentIndex, previous);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        chatBar.tick();
        for (PandoricalComponent component : components) {
            ScreenHelper.tickTree(component);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);

        // Over the items, under the tooltip: the one layer a veil on a slot can live in
        for (PandoricalComponent component : components) {
            ScreenHelper.renderOverlayTree(component, context, mouseX, mouseY, delta);
        }
        this.extractTooltip(context, mouseX, mouseY);
        chatBar.render(this, context, mouseX, mouseY, delta);
    }

    /**
     * The declarative components, drawn before anything else this screen puts up.
     *
     * <p>Order is the whole point of overriding here. {@code extractContents} runs the widget
     * pass first and the slot pass second, and the components used to go in with the slots -
     * which put this screen's own background over every widget on it. Pandorical adds no widgets
     * of its own, so nothing looked wrong until another mod put a button on one of these screens
     * (a recipe book toggle on a crafting station) and watched the panel paint over it every
     * frame: added, laid out, answering clicks, and invisible.
     *
     * <p>Drawn here they land under the widgets AND under the slot items, which is what a
     * background is. Not after super, which is where they were before 26.3 and which painted the
     * panels over the item icons instead - tooltips still worked, the items were not there.
     */
    @Override
    public void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // No pose translation is in effect yet; components carry absolute screen coordinates
        for (PandoricalComponent component : components) {
            ScreenHelper.renderComponentTree(component, context, mouseX, mouseY, delta);
        }
        super.extractContents(context, mouseX, mouseY, delta);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        // Labels are handled by TextComponent; suppress defaults
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
        if (ScreenHelper.dispatchMouseClick(components, click.x(), click.y(), click.button())) {
            return true;
        }
        return super.mouseClicked(click, doubleClick);
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
        // What no component wanted goes to the slot underneath, through the container habits every
        // container screen shares, so their switch and the pad's navigation scroll reach this one too.
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (ScreenHelper.dispatchMouseReleased(components, event.x(), event.y(), event.button())) {
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public List<NavRegion> navRegions() {
        return ScreenHelper.navRegions(components);
    }

    public void applyUpdates(List<ComponentUpdate> updates) {
        updateMemory.record(updates);
        ScreenHelper.applyUpdates(updates, componentIndex);
    }

    /**
     * The recipe book category this screen crafts from, or empty if it is not a station.
     *
     * <p>Public so a recipe-book mod can ask. Pandorical draws nothing for this itself: it has no
     * book of its own and no opinion about whose should appear, only the answer to "what is this
     * screen for" that a book needs before it can offer anything.
     */
    public java.util.Optional<String> getRecipeStation() {
        return screenDef == null ? java.util.Optional.empty() : screenDef.recipeStation();
    }

    /**
     * Top-left corner of the panel on screen.
     *
     * <p>Public because {@link #getRecipeStation()} is: a mod told what this bench crafts will
     * want to put a control on it, and the panel moves with the window and with the recipe book
     * pane. Working it out from screen centre instead only holds while the panel is one
     * particular size.
     */
    public int getPanelX() {
        return this.leftPos;
    }

    public int getPanelY() {
        return this.topPos;
    }

    public String getScreenId() {
        return screenDef != null ? screenDef.screenId() : null;
    }

    @Override
    public void onClose() {
        if (screenDef != null) {
            sendAction("_screen", Map.of());
        }
        super.onClose();
    }

    @Override
    public void removed() {
        super.removed();
        components.forEach(ScreenHelper::removedTree);
    }

    private void sendAction(String componentId, Map<String, String> data) {
        if (screenDef != null) {
            ScreenHelper.sendAction(screenDef.screenId(), componentId, data);
        }
    }
}
