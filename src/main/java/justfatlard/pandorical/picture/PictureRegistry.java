package justfatlard.pandorical.picture;

import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.Picture;
import justfatlard.pandorical.api.PictureApi;
import justfatlard.pandorical.protocol.PicturesS2C;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PictureRegistry implements PictureApi {
    public static final PictureRegistry INSTANCE = new PictureRegistry();

    private record Shown(Entity anchor, Picture picture) {}

    private final Map<UUID, Shown> shown = new ConcurrentHashMap<>();

    private PictureRegistry() {}

    /** Once on each side, before any player connects: it registers the payload type. */
    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(PicturesS2C.TYPE, PicturesS2C.STREAM_CODEC);
        EntityTrackingEvents.START_TRACKING.register((entity, player) -> INSTANCE.sendTo(entity, player));
        EntityTrackingEvents.STOP_TRACKING.register((entity, player) -> {
            if (INSTANCE.shown.containsKey(entity.getUUID())) send(player, PicturesS2C.clear(entity.getId()));
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> INSTANCE.shown.remove(entity.getUUID()));
        PandoricalApi.onPlayerReady(player -> {
            for (Shown picture : INSTANCE.shown.values()) {
                if (PlayerLookup.tracking(picture.anchor()).contains(player)) INSTANCE.sendTo(picture.anchor(), player);
            }
        });
    }

    @Override
    public void show(Entity anchor, Picture picture) {
        if (anchor == null || picture == null) return;
        Picture own = new Picture(picture.columns(), picture.rows(), picture.palette().clone(), picture.cells().clone(),
            picture.pose(), picture.backColor(), picture.thickness());
        shown.put(anchor.getUUID(), new Shown(anchor, own));
        broadcast(anchor, PicturesS2C.show(anchor.getId(), own));
    }

    @Override
    public void paint(Entity anchor, int[] indices, byte[] values) {
        if (anchor == null) return;
        Shown picture = shown.get(anchor.getUUID());
        if (picture == null) return;
        byte[] cells = picture.picture().cells();
        int n = Math.min(indices.length, values.length);
        for (int i = 0; i < n; i++) {
            if (indices[i] >= 0 && indices[i] < cells.length) cells[indices[i]] = values[i];
        }
        broadcast(anchor, PicturesS2C.paint(anchor.getId(), indices, values));
    }

    @Override
    public void clear(Entity anchor) {
        if (anchor == null || shown.remove(anchor.getUUID()) == null) return;
        broadcast(anchor, PicturesS2C.clear(anchor.getId()));
    }

    private void sendTo(Entity anchor, ServerPlayer player) {
        Shown picture = shown.get(anchor.getUUID());
        if (picture != null) send(player, PicturesS2C.show(anchor.getId(), picture.picture()));
    }

    private static void broadcast(Entity anchor, PicturesS2C packet) {
        for (ServerPlayer player : PlayerLookup.tracking(anchor)) send(player, packet);
    }

    private static void send(ServerPlayer player, PicturesS2C packet) {
        if (ServerPlayNetworking.canSend(player, PicturesS2C.TYPE)) ServerPlayNetworking.send(player, packet);
    }
}
