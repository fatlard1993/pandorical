package justfatlard.pandorical.screen;

import justfatlard.pandorical.api.Viewport;
import justfatlard.pandorical.protocol.ViewportC2S;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** What each client has said its window is; {@link Viewport#LEAST} stands until one says. */
public final class Viewports {
    private Viewports() {}

    private static final Map<UUID, Viewport> byPlayer = new ConcurrentHashMap<>();

    public static void declare(ServerPlayer player, ViewportC2S payload) {
        byPlayer.put(player.getUUID(), new Viewport(
            Math.max(Viewport.LEAST.width(), payload.width()),
            Math.max(Viewport.LEAST.height(), payload.height())));
    }

    public static void forget(ServerPlayer player) {
        byPlayer.remove(player.getUUID());
    }

    public static Viewport of(ServerPlayer player) {
        return byPlayer.getOrDefault(player.getUUID(), Viewport.LEAST);
    }
}
