package justfatlard.pandorical.client.settings;

import justfatlard.pandorical.client.screen.PandoricalScreen;
import justfatlard.pandorical.protocol.OpenSettingsC2S;
import justfatlard.pandorical.protocol.ViewportC2S;
import justfatlard.pandorical.settings.SettingsRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/**
 * Reports the window size to the server after the hello, and after each resize settles: the mod
 * menu is rebuilt for each report, and a drag resizes every frame.
 */
@Environment(EnvType.CLIENT)
public final class ViewportReporter {
    private ViewportReporter() {}

    private static final int SETTLE_TICKS = 10;

    private static int sentWidth;
    private static int sentHeight;
    private static int settling;

    public static void send(Minecraft client) {
        sentWidth = client.getWindow().getGuiScaledWidth();
        sentHeight = client.getWindow().getGuiScaledHeight();
        settling = 0;
        if (ClientPlayNetworking.canSend(ViewportC2S.TYPE)) {
            ClientPlayNetworking.send(new ViewportC2S(sentWidth, sentHeight));
        }
    }

    public static void tick(Minecraft client) {
        if (sentWidth == 0 || client.getConnection() == null) return;
        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();
        if (width == sentWidth && height == sentHeight) {
            settling = 0;
            return;
        }
        if (++settling < SETTLE_TICKS) return;
        boolean menuOpen = client.gui.screen() instanceof PandoricalScreen screen
            && screen.getScreenType().equals(SettingsRegistry.SCREEN_TYPE);
        send(client);
        if (menuOpen && ClientPlayNetworking.canSend(OpenSettingsC2S.TYPE)) {
            ClientPlayNetworking.send(new OpenSettingsC2S());
        }
    }

    public static void clear() {
        sentWidth = 0;
        sentHeight = 0;
        settling = 0;
    }
}
