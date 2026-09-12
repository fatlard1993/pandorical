package justfatlard.pandorical.client.component;

import justfatlard.pandorical.screen.PandoricalMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

import java.util.Map;
import java.util.function.BiConsumer;

public record ComponentContext(
    String screenId,
    String screenType,
    int screenX,
    int screenY,
    Font font,
    BiConsumer<String, Map<String, String>> sendAction,
    PandoricalMenu menu // null outside a container screen
) {
    public Minecraft minecraft() {
        return Minecraft.getInstance();
    }
}
