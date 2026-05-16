package justfatlard.pandorical.client.component;

import justfatlard.pandorical.protocol.ComponentDef;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.Map;

/**
 * Client-side component rendered from a server-sent ComponentDef.
 */
public interface PandoricalComponent {
    /**
     * Initialize this component from its definition.
     * Called once when the screen is built.
     */
    void init(ComponentDef def, ComponentContext context);

    /**
     * Render this component. Called every frame.
     */
    void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta);

    /**
     * Handle mouse click. Return true if consumed.
     */
    default boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }

    /**
     * Handle key press. Return true if consumed.
     */
    default boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }

    /**
     * Handle character typed. Return true if consumed.
     */
    default boolean charTyped(char chr, int modifiers) { return false; }

    /**
     * Handle mouse scroll. Return true if consumed.
     */
    default boolean mouseScrolled(double mouseX, double mouseY, double amount) { return false; }

    /**
     * Apply partial property updates from the server.
     */
    void updateProps(Map<String, String> changedProps);

    /**
     * Component bounds for hit testing.
     */
    int getX();
    int getY();
    int getWidth();
    int getHeight();

    default boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= getX() && mouseX < getX() + getWidth()
            && mouseY >= getY() && mouseY < getY() + getHeight();
    }

    /**
     * Child components for recursive rendering and event routing.
     */
    List<PandoricalComponent> getChildren();

    /**
     * Component ID for event routing and updates.
     */
    String getId();
}
