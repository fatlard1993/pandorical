package justfatlard.pandorical.client.screen;

import justfatlard.pandorical.client.component.*;
import justfatlard.pandorical.protocol.ComponentDef;
import justfatlard.pandorical.protocol.ComponentUpdate;
import justfatlard.pandorical.protocol.ScreenActionC2S;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import justfatlard.pandorical.api.NavigableScreen;

/** Component tree logic shared by PandoricalScreen and PandoricalContainerScreen. */
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
        component.placeIn(offsetX, offsetY);
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
        if (!component.isVisible()) return;
        renderWithGeometryTransform(component, graphics, mouseX, mouseY, delta);

        // Children are drawn shifted by the scroll, so the mouse is counter-shifted for hover.
        if (component instanceof ScrollPanelComponent panel) {
            int[] bounds = panel.getClipBounds();
            int scroll = panel.scrollPixels();
            graphics.enableScissor(bounds[0], bounds[1], bounds[2], bounds[3]);
            var pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(0, -scroll);
            for (PandoricalComponent child : component.getChildren()) {
                renderComponentTree(child, graphics, mouseX, mouseY + scroll, delta);
            }
            pose.popMatrix();
            graphics.disableScissor();
            return;
        }

        for (PandoricalComponent child : component.getChildren()) {
            renderComponentTree(child, graphics, mouseX, mouseY, delta);
        }
    }

    /**
     * Scroll-shifted like {@link #renderComponentTree}, but with no geometry transform: overlays
     * sit on vanilla's slot positions, placed from raw bounds.
     */
    public static void renderOverlayTree(PandoricalComponent component, GuiGraphicsExtractor graphics,
                                         int mouseX, int mouseY, float delta) {
        if (!component.isVisible()) return;
        component.renderOverlay(graphics, mouseX, mouseY, delta);

        if (component instanceof ScrollPanelComponent panel) {
            int[] bounds = panel.getClipBounds();
            int scroll = panel.scrollPixels();
            graphics.enableScissor(bounds[0], bounds[1], bounds[2], bounds[3]);
            var pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(0, -scroll);
            for (PandoricalComponent child : component.getChildren()) {
                renderOverlayTree(child, graphics, mouseX, mouseY + scroll, delta);
            }
            pose.popMatrix();
            graphics.disableScissor();
            return;
        }

        for (PandoricalComponent child : component.getChildren()) {
            renderOverlayTree(child, graphics, mouseX, mouseY, delta);
        }
    }

    private static final float GEOMETRY_EPSILON = 0.01f;

    /**
     * Draws an {@link AbstractComponent} at its displayed geometry: size as a scale from the raw
     * top-left corner, position as a translate, scale and rotation about the interpolated centre.
     */
    private static void renderWithGeometryTransform(PandoricalComponent component, GuiGraphicsExtractor graphics,
                                                      int mouseX, int mouseY, float delta) {
        if (!(component instanceof AbstractComponent ac)) {
            component.render(graphics, mouseX, mouseY, delta);
            return;
        }

        AbstractComponent.GeometrySnapshot g = ac.displayedGeometry(delta);
        int rawX = ac.getX(), rawY = ac.getY(), rawW = ac.getWidth(), rawH = ac.getHeight();

        boolean selfSized = ac.selfRendersInterpolatedSize();

        boolean needsTransform = Math.abs(g.scale() - 1f) > GEOMETRY_EPSILON
            || Math.abs(g.rotation()) > GEOMETRY_EPSILON
            || Math.abs(g.x() - rawX) > GEOMETRY_EPSILON
            || Math.abs(g.y() - rawY) > GEOMETRY_EPSILON
            || (!selfSized && (Math.abs(g.width() - rawW) > GEOMETRY_EPSILON
                || Math.abs(g.height() - rawH) > GEOMETRY_EPSILON));

        if (!needsTransform) {
            component.render(graphics, mouseX, mouseY, delta);
            return;
        }

        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        try {
            if (!selfSized && rawW > 0 && Math.abs(g.width() - rawW) > GEOMETRY_EPSILON) {
                pose.scaleAround(g.width() / rawW, 1f, rawX, rawY);
            }
            if (!selfSized && rawH > 0 && Math.abs(g.height() - rawH) > GEOMETRY_EPSILON) {
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

    public static void removedTree(PandoricalComponent component) {
        for (PandoricalComponent child : component.getChildren()) {
            removedTree(child);
        }
        component.removed();
    }

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

    /** Children in reverse order, top-most first. */
    public static boolean mouseClickedTree(PandoricalComponent component, double mouseX, double mouseY, int button) {
        if (!component.isVisible()) return false;
        // Clipped-out scroll panel children are not clickable; the rest are hit where drawn.
        if (component instanceof ScrollPanelComponent panel) {
            int[] clip = panel.getClipBounds();
            if (mouseX < clip[0] || mouseX >= clip[2] || mouseY < clip[1] || mouseY >= clip[3]) {
                return false;
            }
            double shiftedY = mouseY + panel.scrollPixels();
            List<PandoricalComponent> panelChildren = component.getChildren();
            for (int i = panelChildren.size() - 1; i >= 0; i--) {
                if (mouseClickedTree(panelChildren.get(i), mouseX, shiftedY, button)) {
                    return true;
                }
            }
            return component.mouseClicked(mouseX, mouseY, button);
        }
        List<PandoricalComponent> children = component.getChildren();
        for (int i = children.size() - 1; i >= 0; i--) {
            if (mouseClickedTree(children.get(i), mouseX, mouseY, button)) {
                return true;
            }
        }
        return component.mouseClicked(mouseX, mouseY, button);
    }

    public static boolean keyPressedTree(PandoricalComponent component, int keyCode, int scanCode, int modifiers) {
        if (!component.isVisible()) return false;
        for (PandoricalComponent child : component.getChildren()) {
            if (keyPressedTree(child, keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return component.keyPressed(keyCode, scanCode, modifiers);
    }

    public static boolean charTypedTree(PandoricalComponent component, int codepoint) {
        if (!component.isVisible()) return false;
        for (PandoricalComponent child : component.getChildren()) {
            if (charTypedTree(child, codepoint)) {
                return true;
            }
        }
        return component.charTyped((char) codepoint, 0);
    }

    public static boolean mouseScrolledTree(PandoricalComponent component, double mouseX, double mouseY, double amount) {
        if (!component.isVisible()) return false;
        for (PandoricalComponent child : component.getChildren()) {
            if (mouseScrolledTree(child, mouseX, mouseY, amount)) {
                return true;
            }
        }
        return component.mouseScrolled(mouseX, mouseY, amount);
    }

    public static boolean dispatchMouseClick(List<PandoricalComponent> roots, double mouseX, double mouseY, int button) {
        for (int i = roots.size() - 1; i >= 0; i--) {
            if (mouseClickedTree(roots.get(i), mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    public static boolean dispatchMouseReleased(List<PandoricalComponent> roots, double mouseX, double mouseY, int button) {
        boolean consumed = false;
        for (int i = roots.size() - 1; i >= 0; i--) {
            consumed |= mouseReleasedTree(roots.get(i), mouseX, mouseY, button);
        }
        return consumed;
    }

    private static boolean mouseReleasedTree(PandoricalComponent component, double mouseX, double mouseY, int button) {
        boolean consumed = false;
        for (PandoricalComponent child : component.getChildren()) {
            consumed |= mouseReleasedTree(child, mouseX, mouseY, button);
        }
        return component.mouseReleased(mouseX, mouseY, button) || consumed;
    }

    public static boolean dispatchKeyPressed(List<PandoricalComponent> roots, int keyCode, int scanCode, int modifiers) {
        for (PandoricalComponent root : roots) {
            if (keyPressedTree(root, keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    public static boolean dispatchCharTyped(List<PandoricalComponent> roots, int codepoint) {
        for (PandoricalComponent root : roots) {
            if (charTypedTree(root, codepoint)) {
                return true;
            }
        }
        return false;
    }

    public static boolean dispatchMouseScrolled(List<PandoricalComponent> roots, double mouseX, double mouseY, double amount) {
        for (PandoricalComponent root : roots) {
            if (mouseScrolledTree(root, mouseX, mouseY, amount)) {
                return true;
            }
        }
        return false;
    }

    public static List<NavigableScreen.NavRegion> navRegions(List<PandoricalComponent> roots) {
        List<NavigableScreen.NavRegion> regions = new ArrayList<>();
        for (PandoricalComponent component : roots) {
            collectNavRegions(component, 0, null, regions);
        }
        return regions;
    }

    /**
     * Must mirror {@link #mouseClickedTree}: a navigator moves the pointer onto a region and
     * clicks through the mouse path, so regions are where components are drawn, and a region
     * that path would reject must not be reported.
     */
    private static void collectNavRegions(PandoricalComponent component, int scrollShift,
                                          int[] clip, List<NavigableScreen.NavRegion> into) {
        if (!component.isVisible()) return;

        int drawnY = component.getY() - scrollShift;

        if (component.isNavigable() && clickable(clip, component, drawnY)) {
            into.add(new NavigableScreen.NavRegion(
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

    /** Whether a click at the region's centre passes the enclosing scroll panel's clip. */
    private static boolean clickable(int[] clip, PandoricalComponent component, int drawnY) {
        if (clip == null) return true;
        int centerX = component.getX() + component.getWidth() / 2;
        int centerY = drawnY + component.getHeight() / 2;
        return centerX >= clip[0] && centerX < clip[2] && centerY >= clip[1] && centerY < clip[3];
    }
}
