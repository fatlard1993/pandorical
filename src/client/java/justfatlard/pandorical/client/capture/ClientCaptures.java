package justfatlard.pandorical.client.capture;

import com.mojang.blaze3d.platform.NativeImage;
import justfatlard.pandorical.protocol.CapturePartC2S;
import justfatlard.pandorical.protocol.CaptureRequestS2C;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Takes the pictures a server asks for. The HUD is hidden, as F1 hides it, until a frame has been
 * drawn without it, and then the frame is read back the way F2 reads it: so the picture is the
 * world alone, without hotbar, hand or crosshair.
 */
public final class ClientCaptures {
    private ClientCaptures() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("pandorical");
    private static final int NONE = -1;

    private static int asked = NONE;
    private static int side;
    private static int framesSince;
    private static boolean hidHud;

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CaptureRequestS2C.TYPE, (payload, context) ->
            context.client().execute(() -> ask(context.client(), payload)));
        LevelRenderEvents.END_MAIN.register(context -> {
            if (asked != NONE) framesSince++;
        });
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (asked != NONE && framesSince > 0) take(client);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            showHud(client);
            asked = NONE;
        }));
    }

    private static void ask(Minecraft client, CaptureRequestS2C payload) {
        if (asked != NONE) ClientPlayNetworking.send(CapturePartC2S.declined(asked));
        if (client.gui.screen() != null || client.level == null) {
            ClientPlayNetworking.send(CapturePartC2S.declined(payload.id()));
            asked = NONE;
            return;
        }
        asked = payload.id();
        side = payload.side();
        framesSince = 0;
        if (!client.gui.hud.isHidden()) {
            client.gui.hud.toggle();
            hidHud = true;
        }
    }

    private static void take(Minecraft client) {
        int id = asked;
        int size = side;
        asked = NONE;
        if (client.gui.screen() != null) {
            showHud(client);
            ClientPlayNetworking.send(CapturePartC2S.declined(id));
            return;
        }
        Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(), frame -> {
            byte[] png = crop(frame, size);
            client.execute(() -> send(id, png));
        });
        // The frame is copied in the order it was asked for, ahead of the next one drawn
        showHud(client);
    }

    private static byte[] crop(NativeImage frame, int size) {
        Path file = null;
        try (frame; NativeImage square = new NativeImage(size, size, false)) {
            int across = Math.min(frame.getWidth(), frame.getHeight());
            frame.resizeSubRectTo((frame.getWidth() - across) / 2, (frame.getHeight() - across) / 2, across, across, square);
            // NativeImage only writes PNG to a file
            file = Files.createTempFile("pandorical-capture", ".png");
            square.writeToFile(file);
            return Files.readAllBytes(file);
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("[Pandorical] Could not take the picture the server asked for", e);
            return null;
        } finally {
            if (file != null) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void send(int id, byte[] png) {
        if (!ClientPlayNetworking.canSend(CapturePartC2S.TYPE)) return;
        if (png == null) {
            ClientPlayNetworking.send(CapturePartC2S.declined(id));
            return;
        }
        int total = (png.length + CapturePartC2S.MOST_BYTES - 1) / CapturePartC2S.MOST_BYTES;
        for (int index = 0; index < total; index++) {
            int from = index * CapturePartC2S.MOST_BYTES;
            byte[] part = Arrays.copyOfRange(png, from, Math.min(png.length, from + CapturePartC2S.MOST_BYTES));
            ClientPlayNetworking.send(new CapturePartC2S(id, index, total, part));
        }
    }

    private static void showHud(Minecraft client) {
        if (hidHud && client.gui.hud.isHidden()) client.gui.hud.toggle();
        hidHud = false;
    }
}
