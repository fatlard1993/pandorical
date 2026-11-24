package justfatlard.pandorical.client.settings;

import com.mojang.blaze3d.Blaze3D;
import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.client.mods.ModConfigScreens;
import justfatlard.pandorical.settings.ModCatalog;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;

import java.net.URI;

/**
 * What a press on the server's mods screen asks of the player's own machine. The server draws
 * that screen, and can draw one whenever it likes, so each request is honoured only just after the
 * player themselves pressed the button it answers, as this client saw it.
 */
@Environment(EnvType.CLIENT)
public final class ModsScreenActions {
    private ModsScreenActions() {}

    private static final long ANSWER_MS = 5000;

    private static String pressed = "";
    private static long pressedAt;

    /** Every press this client sends on a server's screen, by the component's id. */
    public static void pressed(String componentId) {
        pressed = componentId;
        pressedAt = System.currentTimeMillis();
    }

    /** Whether the last press was on a button of this id or prefix, recently; used up by asking. */
    private static boolean answers(String id) {
        boolean recent = System.currentTimeMillis() - pressedAt < ANSWER_MS
            && (id.endsWith(":") ? pressed.startsWith(id) : pressed.equals(id));
        if (recent) pressed = "";
        return recent;
    }

    /** As the game treats a link in chat: never when the player turned chat links off, and asked about first. */
    public static void openLink(String url) {
        if (!answers("link:")) return;
        openLinkFrom(Minecraft.getInstance().gui.screen(), url);
    }

    /** For a screen of this client's own, whose links come from mods' own metadata. */
    public static void openLinkFrom(Screen parent, String url) {
        Minecraft client = Minecraft.getInstance();
        if (!ModCatalog.isWebLink(url) || !client.options.chatLinks().get()) return;
        URI uri;
        try {
            uri = new URI(url);
        } catch (Exception e) {
            return;
        }
        ConfirmLinkScreen.confirmLinkNow(parent, uri, false);
    }

    public static void openModsFolder() {
        if (!answers("folder")) return;
        try {
            Blaze3D.openPath(ClientModFiles.modsDir());
        } catch (RuntimeException e) {
            Pandorical.LOGGER.warn("[pandorical] could not open the mods folder", e);
        }
    }

    /** For a screen of this client's own: the folder, with no server to answer. */
    public static void openModsFolderNow() {
        pressed("folder");
        openModsFolder();
    }

    public static void openConfig(String mod) {
        if (!answers("configure")) return;
        openConfigOver(Minecraft.getInstance().gui.screen(), mod);
    }

    /** Over {@code parent}, which the config screen returns to; a toast when it would not open. */
    public static void openConfigOver(Screen parent, String mod) {
        Minecraft client = Minecraft.getInstance();
        Screen config = ModConfigScreens.create(mod, parent);
        if (config != null) client.gui.setScreen(config);
        else ClientModFiles.toast("Could not open " + mod + "'s settings", "Its config screen did not open; see the log.");
    }
}
