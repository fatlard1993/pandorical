package justfatlard.pandorical.client.settings;

import justfatlard.pandorical.api.Capabilities;
import justfatlard.pandorical.protocol.OpenSettingsC2S;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import justfatlard.pandorical.client.mods.ModListScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;

/**
 * The Mods button. On the pause and options screens it opens the server's mods screen while the
 * server offers it; otherwise, and on the title screen, the client's own list, unless Mod Menu is
 * installed and has put its own button there.
 */
@Environment(EnvType.CLIENT)
public final class ServerSettingsButton {
    private ServerSettingsButton() {}

    private static final int WIDTH = 110;
    private static final int HEIGHT = 20;
    private static final int MARGIN = 6;
    private static final boolean MOD_MENU = FabricLoader.getInstance().isModLoaded("modmenu");

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            boolean title = screen instanceof TitleScreen;
            if (!(title || screen instanceof PauseScreen || screen instanceof OptionsScreen)) return;
            boolean served = !title && client.getConnection() != null && ServerCapabilities.has(Capabilities.Server.SETTINGS);
            if (!served && MOD_MENU) return;
            Screens.getWidgets(screen).add(Button.builder(Component.translatable("pandorical.mods.button"),
                    button -> {
                        if (served) ClientPlayNetworking.send(new OpenSettingsC2S());
                        else client.gui.setScreen(new ModListScreen(screen));
                    })
                .bounds(scaledWidth - WIDTH - MARGIN, MARGIN, WIDTH, HEIGHT)
                .build());
        });
    }
}
