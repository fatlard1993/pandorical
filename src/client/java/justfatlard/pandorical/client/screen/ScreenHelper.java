package justfatlard.pandorical.client.screen;

import justfatlard.pandorical.client.component.*;
import justfatlard.pandorical.protocol.ComponentDef;
import justfatlard.pandorical.protocol.ComponentUpdate;
import justfatlard.pandorical.protocol.ScreenActionC2S;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

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
        renderWithGeometryTransform(component, graphics, mouseX, mouseY, delta);

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

    private static final float GEOMETRY_EPSILON = 0.01f;

    /**
     * Renders a single component with its interpolated geometry (position/size/scale/rotation)
     * applied as a GUI-space transform, generic to every component type, not just sprite/text,
     * since every {@link AbstractComponent} tracks geometry interpolation unconditionally (see
     * {@code AbstractComponent.interpolatedGeometry}). Components other than {@code AbstractComponent}
     * (none currently registered, but the interface permits custom ones) render unmodified.
     *
     * <p>Width/height changes are applied as a non-uniform scale anchored at the component's raw
     * top-left corner (matching how resizing visually reads as "growing/shrinking from the corner"),
     * position changes as a translate, and scale/rotation props as a uniform scale/rotation anchored
     * at the interpolated center, mirroring the pose-stack technique {@code StructureRenderer}
     * already uses for structure poses, adapted to 2D GUI space via {@code GuiGraphicsExtractor.pose()}
     * (a {@link Matrix3x2fStack}) instead of a 3D {@code PoseStack}.
     *
     * <p>Skips the pose push/pop entirely when nothing differs from the component's raw bounds;
     * the common case for the vast majority of static components every frame.
     */
    private static void renderWithGeometryTransform(PandoricalComponent component, GuiGraphicsExtractor graphics,
                                                      int mouseX, int mouseY, float delta) {
        if (!(component instanceof AbstractComponent ac)) {
            component.render(graphics, mouseX, mouseY, delta);
            return;
        }

        AbstractComponent.GeometrySnapshot g = ac.interpolatedGeometry(delta);
        int rawX = ac.getX(), rawY = ac.getY(), rawW = ac.getWidth(), rawH = ac.getHeight();

        boolean needsTransform = Math.abs(g.scale() - 1f) > GEOMETRY_EPSILON
            || Math.abs(g.rotation()) > GEOMETRY_EPSILON
            || Math.abs(g.x() - rawX) > GEOMETRY_EPSILON
            || Math.abs(g.y() - rawY) > GEOMETRY_EPSILON
            || Math.abs(g.width() - rawW) > GEOMETRY_EPSILON
            || Math.abs(g.height() - rawH) > GEOMETRY_EPSILON;

        if (!needsTransform) {
            component.render(graphics, mouseX, mouseY, delta);
            return;
        }

        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        try {
            if (rawW > 0 && Math.abs(g.width() - rawW) > GEOMETRY_EPSILON) {
                pose.scaleAround(g.width() / rawW, 1f, rawX, rawY);
            }
            if (rawH > 0 && Math.abs(g.height() - rawH) > GEOMETRY_EPSILON) {
                pose.scaleAround(1f, g.height() / rawH, rawX, rawY);
            }
            pose.translate(g.x() - rawX, g.y() - rawY);

            float cx = g.x() + g.width() / 2f;
            float cy = g.y() + g.height() / 2f;
            if (Math.abs(g.rotation()) > GEOMETRY_EPSILON) {
                pose.rotateAbout((float) Math.toRadians(g.rotation()), cx, cy);
            }
            if (Math.abs(g.scale() - 1f) > GEOMETRY_EPSILON) {
                pose.scaleAround(g.scale(), cx, cy);
            }

            component.render(graphics, mouseX, mouseY, delta);
        } finally {
            pose.popMatrix();
        }
    }

    /**
     * Advance client-side interpolation for a component and its whole subtree by one client tick.
     */
    public static void tickTree(PandoricalComponent component) {
        component.tick();
        for (PandoricalComponent child : component.getChildren()) {
            tickTree(child);
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
