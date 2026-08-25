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

import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
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

    // Captured per frame for the component pass inside extractSlots, whose
    // vanilla signature carries no partial tick
    private float frameDelta;

    /**
     * Slots this drag has already emptied, so one sweep moves each stack once.
     *
     * <p>A drag fires every frame the mouse moves, and a slot the pointer lingers on would
     * otherwise be sent across, refilled by the shuffle behind it, and sent across again.
     */
    private final Set<Integer> swept = new HashSet<>();

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
    public void containerTick() {
        super.containerTick();
        for (PandoricalComponent component : components) {
            ScreenHelper.tickTree(component);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // super handles blur/background and the vanilla slot pass; the
        // declarative components render from the extractSlots override below,
        // UNDER the slot items. Rendering them after super (the pre-26.3
        // structure) painted panel backgrounds over the already-drawn item
        // icons: hover tooltips still worked, icons were invisible.
        this.frameDelta = delta;
        super.extractRenderState(context, mouseX, mouseY, delta);

        this.extractTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void extractSlots(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        // extractSlots runs inside extractContents' (leftPos, topPos) pose
        // translation; components carry absolute screen coordinates, so
        // translate back for their pass
        var pose = context.pose();
        pose.pushMatrix();
        pose.translate(-this.leftPos, -this.topPos);
        for (PandoricalComponent component : components) {
            ScreenHelper.renderComponentTree(component, context, mouseX, mouseY, frameDelta);
        }
        pose.popMatrix();

        // Vanilla slot items draw on top of the component panels/frames
        super.extractSlots(context, mouseX, mouseY);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        // Labels are handled by TextComponent; suppress defaults
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
        if (ScreenHelper.dispatchKeyPressed(components, event.key(), event.keycode(), event.modifiers())) {
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
        // A component that wanted the scroll has had it; what is left is the slot underneath.
        if (transferHovered(false)) return true;

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    /**
     * Drag across slots to send them across, the way scroll sends one.
     *
     * <p>Only with an empty hand. A drag while carrying something is vanilla's own gesture for
     * dealing a stack out over several slots, and taking it here would break the more useful of
     * the two.
     */
    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (!this.isQuickCrafting && this.menu.getCarried().isEmpty() && transferHovered(true)) {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        swept.clear();
        return super.mouseReleased(event);
    }

    /**
     * Send whatever is under the pointer to the other half of the screen.
     *
     * <p>Vanilla's own quick-move, which is what shift-click already does: the menu decides where
     * a stack belongs and the move travels by the same packet a shift-click would. Nothing new
     * crosses the wire, and a server that has never heard of this behaves as if the player were
     * unusually quick with the shift key.
     *
     * <p>Direction is deliberately ignored. Quick-move already knows which way a stack goes -
     * out of the container or into it - so asking the wheel to say the same thing again only
     * makes half the gesture do nothing.
     */
    private boolean transferHovered(boolean sweeping) {
        Slot slot = this.hoveredSlot;
        if (slot == null || !slot.hasItem()) return false;
        if (this.minecraft == null || this.minecraft.player == null) return false;
        if (!slot.mayPickup(this.minecraft.player)) return false;
        // Only a drag remembers where it has been. A wheel click is its own event, and a
        // second turn on the same slot means the player wants the next stack too.
        if (sweeping && !swept.add(slot.index)) return true;

        this.slotClicked(slot, slot.index, 0, ContainerInput.QUICK_MOVE);
        return true;
    }

    public void applyUpdates(List<ComponentUpdate> updates) {
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
