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

public class PandoricalContainerScreen extends AbstractContainerScreen<PandoricalMenu> implements NavigableScreen {
    private final OpenScreenS2C screenDef;
    private final ScreenComponents components = new ScreenComponents();

    public PandoricalContainerScreen(PandoricalMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title,
              menu.getScreenDef() != null ? menu.getScreenDef().width() : 176,
              menu.getScreenDef() != null ? menu.getScreenDef().height() : 166);
        this.screenDef = menu.getScreenDef();
        this.inventoryLabelY = 1000;
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

        // Over the slot items, under the tooltip.
        components.renderOverlays(context, mouseX, mouseY, delta);
        this.extractTooltip(context, mouseX, mouseY);
        components.renderChat(this, context, mouseX, mouseY, delta);
    }

    /**
     * Components draw before super so they sit under both other mods' widgets and the slot items;
     * {@code extractContents} draws widgets first, then slots.
     */
    @Override
    public void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        components.render(context, mouseX, mouseY, delta);
        super.extractContents(context, mouseX, mouseY, delta);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor context, int mouseX, int mouseY) {
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

    /** The recipe book category this screen crafts from, for a recipe-book mod to read. */
    public Optional<String> getRecipeStation() {
        return screenDef == null ? Optional.empty() : screenDef.recipeStation();
    }

    /** The panel's top-left on screen; it moves with the recipe book pane. */
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
