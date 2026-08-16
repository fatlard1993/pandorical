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
    public void tick() {
        super.tick();
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

    @Override
    public List<NavRegion> navRegions() {
        List<NavRegion> regions = new ArrayList<>();
        for (PandoricalComponent component : components) {
            collectNavRegions(component, 0, null, regions);
        }
        return regions;
    }

    /**
     * Walks the whole tree rather than the roots, because a navigable
     * component is usually a child: buttons live inside panels, and a panel
     * itself is not something to land on. Descends into non-navigable
     * components for the same reason, and does not stop at one that is —
     * nesting a button inside a button is not a shape this forbids.
     *
     * <p>Regions must describe where a component is <em>drawn</em>, not where it
     * was laid out, and inside a scroll panel those differ: children keep their
     * built positions and the panel draws them shifted up by its scroll
     * displacement. Reporting the built position sends a navigator to empty
     * space. This mirrors {@code ScreenHelper.mouseClickedTree} exactly, which
     * is the contract that matters: a navigator reaches a component by moving
     * the pointer onto the region and clicking through the ordinary mouse path,
     * so a region the click path would reject is worse than no region at all.
     */
    private static void collectNavRegions(PandoricalComponent component, int scrollShift,
                                          int[] clip, List<NavRegion> into) {
        int drawnY = component.getY() - scrollShift;

        if (component.isNavigable() && clickable(clip, component, drawnY)) {
            into.add(new NavRegion(
                component.getId(),
                component.getX(), drawnY,
                component.getWidth(), component.getHeight()));
        }

        int childShift = scrollShift;
        int[] childClip = clip;
        if (component instanceof ScrollPanelComponent panel) {
            childShift = scrollShift + panel.scrollPixels();
            childClip = panel.getClipBounds();
        }

        for (PandoricalComponent child : component.getChildren()) {
            collectNavRegions(child, childShift, childClip, into);
        }
    }

    /**
     * Whether a click at the region's centre would survive the enclosing scroll
     * panel's clip test. A component scrolled out of view still exists in the
     * tree; without this it becomes a target that silently swallows presses.
     */
    private static boolean clickable(int[] clip, PandoricalComponent component, int drawnY) {
        if (clip == null) return true;
        int centerX = component.getX() + component.getWidth() / 2;
        int centerY = drawnY + component.getHeight() / 2;
        return centerX >= clip[0] && centerX < clip[2] && centerY >= clip[1] && centerY < clip[3];
    }

    private void sendAction(String componentId, Map<String, String> data) {
        ScreenHelper.sendAction(screenDef.screenId(), componentId, data);
    }
}
