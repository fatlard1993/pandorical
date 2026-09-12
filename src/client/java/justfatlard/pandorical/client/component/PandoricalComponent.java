package justfatlard.pandorical.client.component;

import justfatlard.pandorical.protocol.ComponentDef;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.Map;

/** Client-side component rendered from a server-sent ComponentDef. */
public interface PandoricalComponent {
    void init(ComponentDef def, ComponentContext context);

    void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta);

    /** Drawn by a container screen after vanilla's slot items, for what belongs over an item. */
    default void renderOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {}

    /** A hidden component and its children are skipped by every draw, hit and navigation walk. */
    default boolean isVisible() { return true; }

    // Input handlers return true when the event is consumed.

    default boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
    /** Offered to every component, not only the one under the pointer. */
    default boolean mouseReleased(double mouseX, double mouseY, int button) { return false; }

    default boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }

    default boolean charTyped(char chr, int modifiers) { return false; }

    default boolean mouseScrolled(double mouseX, double mouseY, double amount) { return false; }

    /**
     * The origin the server's x and y are measured from: the parent's corner, or the screen's at
     * the top. Called once, as it is built; later position props are measured from it too.
     */
    default void placeIn(int originX, int originY) {}

    /** The parent moved by this much; move with it, children included. */
    default void shiftOrigin(int dx, int dy) {}

    void updateProps(Map<String, String> changedProps);

    default void tick() {}

    /**
     * The screen closed, was replaced, or was rebuilt for a resize. Restore anything borrowed
     * from the window here, such as a hidden cursor.
     */
    default void removed() {}

    /**
     * Called when a resize rebuilds the screen. {@code previous} has the same id and class and has
     * already been {@link #removed}. Props are replayed separately; carry over only client-side
     * state the server never sent, such as typed text.
     */
    default void carryOverFrom(PandoricalComponent previous) {}

    int getX();
    int getY();
    int getWidth();
    int getHeight();

    default boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= getX() && mouseX < getX() + getWidth()
            && mouseY >= getY() && mouseY < getY() + getHeight();
    }

    List<PandoricalComponent> getChildren();

    String getId();

    /**
     * Whether a gamepad or keyboard navigator can land here. Return true exactly when
     * {@link #mouseClicked} handles clicks.
     *
     * @see justfatlard.pandorical.api.NavigableScreen
     */
    default boolean isNavigable() { return false; }
}
