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
 * Tells the server how big this window is, so the screens it builds can fill it.
 *
 * <p>Once after the hello, and again whenever the window has sat at a new size for a moment:
 * a drag of the corner resizes every frame, and a screen rebuilt every frame would be the mod
 * menu flickering through the drag. If the menu is open when the size settles, it is asked for
 * again, since the one showing was built for the old window.
 */
@Environment(EnvType.CLIENT)
public final class ViewportReporter {
    private ViewportReporter() {}

    /** Ticks the window must hold a size before it is reported. */
    private static final int SETTLE_TICKS = 10;

    private static int sentWidth;
    private static int sentHeight;
    private static int settling;

    /** After the hello: the size as it is now, with nothing to wait for. */
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
