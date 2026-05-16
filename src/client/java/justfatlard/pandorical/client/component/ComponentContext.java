package justfatlard.pandorical.client.component;

import justfatlard.pandorical.screen.PandoricalMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Context passed to components during initialization and rendering.
 * Provides access to screen-level resources and event dispatch.
 */
public record ComponentContext(
    String screenId,
    String screenType,
    int screenX,
    int screenY,
    Font font,
    BiConsumer<String, Map<String, String>> sendAction,
    PandoricalMenu menu // null for non-container screens
) {
    public Minecraft minecraft() {
        return Minecraft.getInstance();
    }
}
