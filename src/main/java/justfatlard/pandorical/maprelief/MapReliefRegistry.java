package justfatlard.pandorical.maprelief;

import justfatlard.pandorical.api.MapReliefApi;
import justfatlard.pandorical.api.MapTerrain;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.protocol.MapReliefS2C;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ItemFrame;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The server's record of every framed map shown in relief, and who is sent what as tracking comes and goes. */
public final class MapReliefRegistry implements MapReliefApi {
    public static final MapReliefRegistry INSTANCE = new MapReliefRegistry();

    private record Shown(ItemFrame frame, MapTerrain terrain) {}

    private final Map<UUID, Shown> shown = new ConcurrentHashMap<>();

    private MapReliefRegistry() {}

    /** Called once from Pandorical's initializer, on both sides, before any player connects. */
    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(MapReliefS2C.TYPE, MapReliefS2C.STREAM_CODEC);
        EntityTrackingEvents.START_TRACKING.register((entity, player) -> INSTANCE.sendTo(entity.getUUID(), player));
        EntityTrackingEvents.STOP_TRACKING.register((entity, player) -> {
            if (INSTANCE.shown.containsKey(entity.getUUID())) send(player, MapReliefS2C.clear(entity.getId(), MapReliefS2C.Motion.NONE));
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> INSTANCE.shown.remove(entity.getUUID()));
        // Tracking can begin before a joining client has said what it understands; say it again once it has
        PandoricalApi.onPlayerReady(player -> {
            for (Shown relief : INSTANCE.shown.values()) {
                if (PlayerLookup.tracking(relief.frame()).contains(player)) INSTANCE.sendTo(relief.frame().getUUID(), player);
            }
        });
    }

    @Override
    public void show(ItemFrame frame, MapTerrain terrain) {
        put(frame, terrain, MapReliefS2C.Motion.NONE);
    }

    @Override
    public void raise(ItemFrame frame, MapTerrain terrain) {
        put(frame, terrain, MapReliefS2C.Motion.RISE);
    }

    @Override
    public void clear(ItemFrame frame) {
        take(frame, MapReliefS2C.Motion.NONE);
    }

    @Override
    public void lower(ItemFrame frame) {
        take(frame, MapReliefS2C.Motion.SINK);
    }

    private void put(ItemFrame frame, MapTerrain terrain, MapReliefS2C.Motion motion) {
        if (frame == null || terrain == null) return;
        shown.put(frame.getUUID(), new Shown(frame, terrain));
        broadcast(frame, MapReliefS2C.show(frame.getId(), terrain, motion));
    }

    private void take(ItemFrame frame, MapReliefS2C.Motion motion) {
        if (frame == null || shown.remove(frame.getUUID()) == null) return;
        broadcast(frame, MapReliefS2C.clear(frame.getId(), motion));
    }

    private void sendTo(UUID frame, ServerPlayer player) {
        Shown relief = shown.get(frame);
        if (relief != null) send(player, MapReliefS2C.show(relief.frame().getId(), relief.terrain(), MapReliefS2C.Motion.NONE));
    }

    private static void broadcast(ItemFrame frame, MapReliefS2C packet) {
        for (ServerPlayer player : PlayerLookup.tracking(frame)) send(player, packet);
    }

    private static void send(ServerPlayer player, MapReliefS2C packet) {
        if (ServerPlayNetworking.canSend(player, MapReliefS2C.TYPE)) ServerPlayNetworking.send(player, packet);
    }
}
