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
public class PandoricalContainerScreen extends AbstractContainerScreen<PandoricalMenu> {
    private final OpenScreenS2C screenDef;
    private final List<PandoricalComponent> components = new ArrayList<>();
    private final Map<String, PandoricalComponent> componentIndex = new HashMap<>();

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
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        this.extractBackground(context, mouseX, mouseY, delta);

        // Render declarative components
        for (PandoricalComponent component : components) {
            ScreenHelper.renderComponentTree(component, context, mouseX, mouseY, delta);
        }

        // Render vanilla slot overlay (items, hover highlights)
        super.extractRenderState(context, mouseX, mouseY, delta);

        this.extractTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        // Labels are handled by TextComponent — suppress defaults
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean handled) {
        if (!handled && ScreenHelper.dispatchMouseClick(components, click.x(), click.y(), click.button())) {
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

    public void applyUpdates(List<ComponentUpdate> updates) {
        ScreenHelper.applyUpdates(updates, componentIndex);
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

    private void sendAction(String componentId, Map<String, String> data) {
        if (screenDef != null) {
            ScreenHelper.sendAction(screenDef.screenId(), componentId, data);
        }
    }
}
