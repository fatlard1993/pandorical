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

        // Scroll panels scissor-clip their children and translate them by the
        // scroll displacement; the mouse is counter-shifted so hover states
        // land on the child actually under the cursor
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
     * The over-the-items pass, walked the same way as {@link #renderComponentTree} so a grid
     * inside a scroll panel veils the slots where they are drawn, not where they were laid out.
     * No geometry transform: what this pass draws sits on vanilla's slot positions, which the
     * grid has already placed from its raw bounds.
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

        AbstractComponent.GeometrySnapshot g = ac.displayedGeometry(delta);
        int rawX = ac.getX(), rawY = ac.getY(), rawW = ac.getWidth(), rawH = ac.getHeight();

        // Components that re-clip themselves at the interpolated size (see
        // AbstractComponent.selfRendersInterpolatedSize) must not ALSO be
        // scaled by it, or the reveal squashes
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

    /** Tell a component and its whole subtree that their screen has gone. */
    public static void removedTree(PandoricalComponent component) {
        for (PandoricalComponent child : component.getChildren()) {
            removedTree(child);
        }
        component.removed();
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
        if (!component.isVisible()) return false;
        // A scroll panel's clipped-out children are invisible and must not be
        // clickable: only descend into its children when the click lands
        // inside the clip region (mirroring the render-time scissor), and
        // counter-shift the mouse by the scroll displacement so the click
        // hits the child actually drawn under the cursor
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

    /**
     * Route key press through component tree.
     */
    public static boolean keyPressedTree(PandoricalComponent component, int keyCode, int scanCode, int modifiers) {
        if (!component.isVisible()) return false;
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
        if (!component.isVisible()) return false;
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
        if (!component.isVisible()) return false;
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
     * Dispatch a mouse release through a list of root components: to all of them, so a
     * component holding a press hears the release however far the pointer has wandered.
     */
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

    /** Where the components in these trees can be pressed, for a {@link justfatlard.pandorical.api.NavigableScreen}. */
    public static List<NavigableScreen.NavRegion> navRegions(List<PandoricalComponent> roots) {
        List<NavigableScreen.NavRegion> regions = new ArrayList<>();
        for (PandoricalComponent component : roots) {
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
                                          int[] clip, List<NavigableScreen.NavRegion> into) {
        // Hidden is hidden for the navigator too, children included: the click path
        // this mirrors rejects the whole subtree, so a region for any of it would be
        // a target that swallows presses
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
}
