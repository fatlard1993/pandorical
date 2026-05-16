package justfatlard.pandorical.client.screen;

import justfatlard.pandorical.client.component.*;
import justfatlard.pandorical.protocol.ComponentDef;
import justfatlard.pandorical.protocol.ComponentUpdate;
import justfatlard.pandorical.protocol.ScreenActionC2S;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.Map;

/**
 * Shared logic for PandoricalScreen and PandoricalContainerScreen.
 * Extracted to avoid duplication across the two screen base classes.
 */
public final class ScreenHelper {
    private ScreenHelper() {}

    public static PandoricalComponent buildComponent(
            ComponentDef def, ComponentContext context,
            int offsetX, int offsetY,
            Map<String, PandoricalComponent> componentIndex) {
        PandoricalComponent component = ComponentRegistry.create(def.type());

        ComponentDef absoluteDef = new ComponentDef(
            def.id(), def.type(),
            def.x() + offsetX, def.y() + offsetY,
            def.width(), def.height(),
            def.props(), def.children()
        );

        component.init(absoluteDef, context);
        componentIndex.put(def.id(), component);

        for (ComponentDef childDef : def.children()) {
            PandoricalComponent child = buildComponent(childDef, context,
                def.x() + offsetX, def.y() + offsetY, componentIndex);
            component.getChildren().add(child);
        }

        return component;
    }

    public static void renderComponentTree(PandoricalComponent component, GuiGraphicsExtractor graphics,
                                            int mouseX, int mouseY, float delta) {
        component.render(graphics, mouseX, mouseY, delta);

        // Apply scissor clipping for scroll panels
        boolean clipping = component instanceof ScrollPanelComponent;
        if (clipping) {
            int[] bounds = ((ScrollPanelComponent) component).getClipBounds();
            graphics.enableScissor(bounds[0], bounds[1], bounds[2], bounds[3]);
        }

        for (PandoricalComponent child : component.getChildren()) {
            renderComponentTree(child, graphics, mouseX, mouseY, delta);
        }

        if (clipping) {
            graphics.disableScissor();
        }
    }

    public static void applyUpdates(List<ComponentUpdate> updates, Map<String, PandoricalComponent> componentIndex) {
        for (ComponentUpdate update : updates) {
            PandoricalComponent component = componentIndex.get(update.componentId());
            if (component != null) {
                component.updateProps(update.changedProps());
            }
        }
    }

    public static void sendAction(String screenId, String componentId, Map<String, String> data) {
        ClientPlayNetworking.send(new ScreenActionC2S(
            screenId, componentId,
            componentId.equals("_screen") ? "close" : "click",
            data
        ));
    }

    /**
     * Route mouse click through component tree in reverse order (top-most first).
     */
    public static boolean mouseClickedTree(PandoricalComponent component, double mouseX, double mouseY, int button) {
        List<PandoricalComponent> children = component.getChildren();
        for (int i = children.size() - 1; i >= 0; i--) {
            if (mouseClickedTree(children.get(i), mouseX, mouseY, button)) {
                return true;
            }
        }
        return component.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Route key press through component tree.
     */
    public static boolean keyPressedTree(PandoricalComponent component, int keyCode, int scanCode, int modifiers) {
        for (PandoricalComponent child : component.getChildren()) {
            if (keyPressedTree(child, keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return component.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Route character typed through component tree.
     */
    public static boolean charTypedTree(PandoricalComponent component, int codepoint) {
        for (PandoricalComponent child : component.getChildren()) {
            if (charTypedTree(child, codepoint)) {
                return true;
            }
        }
        return component.charTyped((char) codepoint, 0);
    }

    /**
     * Route mouse scroll through component tree.
     */
    public static boolean mouseScrolledTree(PandoricalComponent component, double mouseX, double mouseY, double amount) {
        for (PandoricalComponent child : component.getChildren()) {
            if (mouseScrolledTree(child, mouseX, mouseY, amount)) {
                return true;
            }
        }
        return component.mouseScrolled(mouseX, mouseY, amount);
    }

    /**
     * Dispatch a mouse event through a list of root components (reverse order).
     */
    public static boolean dispatchMouseClick(List<PandoricalComponent> roots, double mouseX, double mouseY, int button) {
        for (int i = roots.size() - 1; i >= 0; i--) {
            if (mouseClickedTree(roots.get(i), mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Dispatch a key event through a list of root components.
     */
    public static boolean dispatchKeyPressed(List<PandoricalComponent> roots, int keyCode, int scanCode, int modifiers) {
        for (PandoricalComponent root : roots) {
            if (keyPressedTree(root, keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Dispatch a char typed event through a list of root components.
     */
    public static boolean dispatchCharTyped(List<PandoricalComponent> roots, int codepoint) {
        for (PandoricalComponent root : roots) {
            if (charTypedTree(root, codepoint)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Dispatch a mouse scroll event through a list of root components.
     */
    public static boolean dispatchMouseScrolled(List<PandoricalComponent> roots, double mouseX, double mouseY, double amount) {
        for (PandoricalComponent root : roots) {
            if (mouseScrolledTree(root, mouseX, mouseY, amount)) {
                return true;
            }
        }
        return false;
    }
}
