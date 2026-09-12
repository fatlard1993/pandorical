package justfatlard.pandorical.client.settings;

import justfatlard.pandorical.protocol.OpenSettingsC2S;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import justfatlard.pandorical.api.Capabilities;

/**
 * The way in from the pause menu and the options menu.
 *
 * <p>A button in the corner of both, shown only while connected to a server that has the mod
 * menu to offer, which asks the server for the screen. The server builds it, so the client knows
 * nothing about what is on it.
 */
@Environment(EnvType.CLIENT)
public final class ServerSettingsButton {
    private ServerSettingsButton() {}

    private static final int WIDTH = 110;
    private static final int HEIGHT = 20;
    private static final int MARGIN = 6;

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            boolean pause = screen instanceof PauseScreen;
            if (!(pause || screen instanceof OptionsScreen) || !ServerCapabilities.has(Capabilities.SETTINGS)) return;
            if (client.getConnection() == null) return;
            Screens.getWidgets(screen).add(Button.builder(Component.translatable("pandorical.mods.button"),
                    button -> ClientPlayNetworking.send(new OpenSettingsC2S()))
                .bounds(scaledWidth - WIDTH - MARGIN, MARGIN, WIDTH, HEIGHT)
                .build());
        });
    }
}
