package justfatlard.pandorical.entitymodel;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.api.EntityModelApi;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.protocol.EntityModelS2C;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which entities are drawn with which model, told to whoever is tracking them.
 *
 * <p>Shaped like the entity overlays and pictures: kept by UUID, sent on start of tracking and to a
 * player whose handshake finished after tracking began, dropped on unload. Asked by channel rather
 * than capability, so a client older than 15.16 is sent nothing and draws the entity as vanilla does.
 */
public final class EntityModelRegistry implements EntityModelApi {
    public static final EntityModelRegistry INSTANCE = new EntityModelRegistry();

    private record Assigned(Entity entity, EntityModelS2C packet) {}

    private final Map<UUID, Assigned> assigned = new ConcurrentHashMap<>();

    private EntityModelRegistry() {}

    /** Once on each side, before any player connects: it registers the payload type. */
    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(EntityModelS2C.TYPE, EntityModelS2C.STREAM_CODEC);
        EntityTrackingEvents.START_TRACKING.register((entity, player) -> INSTANCE.sendTo(entity, player));
        // Only the entity that was assigned: a mob converting into another can hand its UUID to the
        // new one before the old one unloads, and the new one may be assigned by then.
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) ->
            INSTANCE.assigned.computeIfPresent(entity.getUUID(), (uuid, a) -> a.entity() == entity ? null : a));
        PandoricalApi.onPlayerReady(player -> {
            for (Assigned a : INSTANCE.assigned.values()) {
                if (PlayerLookup.tracking(a.entity()).contains(player)) INSTANCE.sendTo(a.entity(), player);
            }
        });
    }

    @Override
    public void set(LivingEntity entity, Identifier model, @Nullable Identifier texture, float drawScale) {
        if (entity == null || model == null) return;
        if (!(drawScale > 0F) || Float.isInfinite(drawScale)) {
            Pandorical.LOGGER.warn("Ignoring entity model {} with draw scale {}", model, drawScale);
            return;
        }
        EntityModelS2C packet = new EntityModelS2C(entity.getId(), model.toString(),
            texture == null ? "" : texture.toString(), drawScale);
        Assigned was = assigned.put(entity.getUUID(), new Assigned(entity, packet));
        // Usually said again on every load; only news is worth a packet.
        if (was != null && was.entity() == entity && Objects.equals(was.packet(), packet)) return;
        broadcast(entity, packet);
    }

    @Override
    public void clear(LivingEntity entity) {
        if (entity == null || assigned.remove(entity.getUUID()) == null) return;
        broadcast(entity, EntityModelS2C.clear(entity.getId()));
    }

    @Override
    public @Nullable Identifier get(LivingEntity entity) {
        Assigned a = entity == null ? null : assigned.get(entity.getUUID());
        return a == null || a.entity() != entity ? null : Identifier.tryParse(a.packet().model());
    }

    private void sendTo(Entity entity, ServerPlayer player) {
        Assigned a = assigned.get(entity.getUUID());
        if (a != null && a.entity() == entity) send(player, a.packet());
    }

    private static void broadcast(Entity entity, EntityModelS2C packet) {
        // Not yet in a level that tracks it (still being finished for a spawn): its trackers hear
        // from START_TRACKING once it is added.
        if (!(entity.level() instanceof ServerLevel)) return;
        for (ServerPlayer player : PlayerLookup.tracking(entity)) send(player, packet);
    }

    private static void send(ServerPlayer player, EntityModelS2C packet) {
        if (ServerPlayNetworking.canSend(player, EntityModelS2C.TYPE)) ServerPlayNetworking.send(player, packet);
    }
}
