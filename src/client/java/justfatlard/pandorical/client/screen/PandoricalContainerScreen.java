package justfatlard.pandorical.client.screen;

import justfatlard.pandorical.client.component.*;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import justfatlard.pandorical.api.NavigableScreen;

/**
 * Container screen with declarative UI + vanilla slot sync.
 * Used for screens that manage item slots (trade, backpack, etc.).
 */
public class PandoricalContainerScreen extends AbstractContainerScreen<PandoricalMenu> implements NavigableScreen {
    private final OpenScreenS2C screenDef;
    private final ScreenComponents components = new ScreenComponents();

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

        if (screenDef == null) {
            components.clear();
            return;
        }

        ComponentContext context = new ComponentContext(
            screenDef.screenId(),
            screenDef.screenType(),
            this.leftPos, this.topPos,
            this.font,
            this::sendAction,
            this.menu
        );

        components.rebuild(screenDef.components(), context, this.leftPos, this.topPos);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        components.tick();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);

        // Over the items, under the tooltip: the one layer a veil on a slot can live in
        components.renderOverlays(context, mouseX, mouseY, delta);
        this.extractTooltip(context, mouseX, mouseY);
        components.renderChat(this, context, mouseX, mouseY, delta);
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
        components.render(context, mouseX, mouseY, delta);
        super.extractContents(context, mouseX, mouseY, delta);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        // Labels are handled by TextComponent; suppress defaults
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
        if (components.mouseClicked(click)) {
            return true;
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (components.keyPressed(event)) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (components.charTyped(event)) {
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (components.mouseScrolled(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        // What no component wanted goes to the slot underneath, through the container habits every
        // container screen shares, so their switch and the pad's navigation scroll reach this one too.
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (components.mouseReleased(event)) {
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public List<NavRegion> navRegions() {
        return components.navRegions();
    }

    public void applyUpdates(List<ComponentUpdate> updates) {
        components.applyUpdates(updates);
    }

    /**
     * The recipe book category this screen crafts from, or empty if it is not a station.
     *
     * <p>Public so a recipe-book mod can ask. Pandorical draws nothing for this itself: it has no
     * book of its own and no opinion about whose should appear, only the answer to "what is this
     * screen for" that a book needs before it can offer anything.
     */
    public Optional<String> getRecipeStation() {
        return screenDef == null ? Optional.empty() : screenDef.recipeStation();
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
        components.removed();
    }

    private void sendAction(String componentId, Map<String, String> data) {
        if (screenDef != null) {
            ScreenHelper.sendAction(screenDef.screenId(), componentId, data);
        }
    }
}
