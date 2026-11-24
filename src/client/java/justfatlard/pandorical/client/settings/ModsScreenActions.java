package justfatlard.pandorical.client.settings;

import com.mojang.blaze3d.Blaze3D;
import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.client.screen.PandoricalScreen;
import justfatlard.pandorical.settings.SettingsRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;

import java.net.URI;
import java.util.Locale;

/**
 * What a press on the mods screen asks of the player's own machine. Each is honoured only while
 * that screen is open, so a server cannot open links or folders on somebody's desktop unasked.
 */
@Environment(EnvType.CLIENT)
public final class ModsScreenActions {
    private ModsScreenActions() {}

    private static long lastFolder;

    /** Through the game's own confirmation, which names the link before anything opens it. */
    public static void openLink(String url) {
        Minecraft client = Minecraft.getInstance();
        if (!modsScreenOpen(client)) return;
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) return;
        URI uri;
        try {
            uri = new URI(url);
        } catch (Exception e) {
            return;
        }
        ConfirmLinkScreen.confirmLinkNow(client.gui.screen(), uri);
    }

    /** At most once a second, however often it is asked. */
    public static void openModsFolder() {
        Minecraft client = Minecraft.getInstance();
        if (!modsScreenOpen(client)) return;
        long now = System.currentTimeMillis();
        if (now - lastFolder < 1000) return;
        lastFolder = now;
        try {
            Blaze3D.openPath(ClientModFiles.modsDir());
        } catch (RuntimeException e) {
            Pandorical.LOGGER.warn("[pandorical] could not open the mods folder", e);
        }
    }

    private static boolean modsScreenOpen(Minecraft client) {
        return client.gui.screen() instanceof PandoricalScreen screen && SettingsRegistry.SCREEN_TYPE.equals(screen.getScreenType());
    }
}
