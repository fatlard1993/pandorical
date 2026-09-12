package justfatlard.pandorical.client.component;

import justfatlard.pandorical.protocol.ComponentDef;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.Map;

/**
 * Client-side component rendered from a server-sent ComponentDef.
 */
public interface PandoricalComponent {
    /** Called once when the screen is built. */
    void init(ComponentDef def, ComponentContext context);

    /** Called every frame. */
    void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta);

    /**
     * A second pass a container screen makes after vanilla has drawn the slot items, for the
     * few things that belong over an item rather than under it. Nothing by default.
     */
    default void renderOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {}

    /**
     * Whether the component is there at all. A hidden component is skipped by every tree walk:
     * not drawn, not hit, not navigable, children included. See
     * {@link justfatlard.pandorical.api.ComponentType#PROP_VISIBLE}.
     */
    default boolean isVisible() { return true; }

    // Input handlers return true when the event is consumed.

    default boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
    /** Offered to every component, not only the one under the pointer: what was pressed must hear its release wherever the mouse went. */
    default boolean mouseReleased(double mouseX, double mouseY, int button) { return false; }

    default boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }

    default boolean charTyped(char chr, int modifiers) { return false; }

    default boolean mouseScrolled(double mouseX, double mouseY, double amount) { return false; }

    /**
     * Where this component's x and y are measured from: its parent's corner, or the screen's for a
     * component at the top. Told once, as it is built. A position the server sends later is measured
     * from here too, the same as the one it was built with.
     */
    default void placeIn(int originX, int originY) {}

    /** Its parent has moved: move with it, by as much, children and all. */
    default void shiftOrigin(int dx, int dy) {}

    /** Apply partial property updates from the server. */
    void updateProps(Map<String, String> changedProps);

    /**
     * Advance client-side interpolation progress by one client tick.
     * No-op by default; {@link AbstractComponent} overrides this to advance geometry/color blending.
     */
    default void tick() {}

    /**
     * The screen this belongs to has gone: closed, replaced, or rebuilt for a resize. The place
     * to put back anything borrowed from the window, such as the cursor a dial hides. Nothing
     * by default.
     */
    default void removed() {}

    // Component bounds for hit testing
    int getX();
    int getY();
    int getWidth();
    int getHeight();

    default boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= getX() && mouseX < getX() + getWidth()
            && mouseY >= getY() && mouseY < getY() + getHeight();
    }

    /** Child components for recursive rendering and event routing. */
    List<PandoricalComponent> getChildren();

    /** Component ID for event routing and updates. */
    String getId();

    /**
     * Whether a non-mouse navigator (gamepad, keyboard focus) should be able
     * to land on this component. Defaults to false, so a component is
     * unreachable until it opts in — the safe direction, since landing on a
     * component that ignores clicks is a dead end the player has to back out
     * of manually.
     *
     * <p>Overriding this is the whole contract: anything that handles
     * {@link #mouseClicked} should return true, and nothing else should.
     *
     * @see justfatlard.pandorical.api.NavigableScreen
     */
    default boolean isNavigable() { return false; }
}
