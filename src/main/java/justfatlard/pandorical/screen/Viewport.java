package justfatlard.pandorical.screen;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import justfatlard.pandorical.protocol.ViewportC2S;
import net.minecraft.server.level.ServerPlayer;

/**
 * The size of each player's window, in scaled pixels, for a screen built to fit it.
 *
 * <p>A client that has not said is taken to have the smallest window vanilla allows at any GUI
 * scale, which is what every screen was sized for before the client could say otherwise.
 */
public record Viewport(int width, int height) {
    public static final Viewport LEAST = new Viewport(320, 240);

    private static final Map<UUID, Viewport> byPlayer = new ConcurrentHashMap<>();

    public static void declare(ServerPlayer player, ViewportC2S payload) {
        byPlayer.put(player.getUUID(), new Viewport(
            Math.max(LEAST.width, payload.width()), Math.max(LEAST.height, payload.height())));
    }

    public static void forget(ServerPlayer player) {
        byPlayer.remove(player.getUUID());
    }

    public static Viewport of(ServerPlayer player) {
        return byPlayer.getOrDefault(player.getUUID(), LEAST);
    }
}
