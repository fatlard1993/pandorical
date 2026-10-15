package justfatlard.pandorical.entitykind;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.api.EntityRegistration;
import justfatlard.pandorical.mixin.SynchedEntityDataAccessor;
import justfatlard.pandorical.protocol.EntityBodiesConfigS2C;
import justfatlard.pandorical.protocol.EntityKindsConfigS2C;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entity types registered through {@link EntityRegistration}, and who can be shown them.
 *
 * <p>A client knows a kind only if it was told in the configuration phase, because that is when it
 * builds the stand-in. So which players can be sent one is recorded there, by profile, rather than
 * read off the play handshake, which lands after the first entities have already been paired. Kinds
 * on a base and kinds with their own body are told on separate channels, and a client can know the
 * first and not the second.
 */
public final class EntityKinds {
    private EntityKinds() {}

    /** The generic client classes a body can be built as, most specific first. */
    public static final String TIER_AGEABLE = "ageable";
    public static final String TIER_MOB = "mob";

    /** What a body syncs, read off one made on the server, and the client class it is built as. */
    public record Layout(String tier, List<Integer> serializers) {}

    private static final Map<EntityType<?>, EntityRegistration> KINDS = new ConcurrentHashMap<>();
    private static final Set<UUID> UNDERSTOOD = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> UNDERSTOOD_BODIES = ConcurrentHashMap.newKeySet();
    /** Types already checked against their base, so the check runs once each. */
    private static final Set<EntityType<?>> CHECKED = ConcurrentHashMap.newKeySet();
    /** Bodies' layouts, read when the server starts. A body without one cannot be sent to anyone's Pandorical. */
    private static final Map<EntityType<?>, Layout> LAYOUTS = new ConcurrentHashMap<>();
    /** Fallbacks that cannot read the entity's data, so vanilla clients are not shown it after all. */
    private static final Set<EntityType<?>> UNREADABLE_FALLBACK = ConcurrentHashMap.newKeySet();

    public static void register(EntityType<?> type, EntityRegistration registration) {
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (BuiltInRegistries.ENTITY_TYPE.getResourceKey(type).isEmpty()) {
            throw new IllegalStateException("[pandorical] registerEntity before the entity type is registered");
        }
        if (registration.hasOwnBody()) {
            if (registration.getModel() == null) {
                throw new IllegalArgumentException("[pandorical] " + id + " has neither a base nor a model: "
                    + "the client has nothing to draw it with");
            }
        } else if (!vanilla(registration.getBase())) {
            throw new IllegalArgumentException("[pandorical] " + id + "'s base must be a vanilla type, so every client has it");
        }
        EntityType<?> fallback = registration.getVanillaFallback();
        if (fallback != null && !vanilla(fallback)) {
            throw new IllegalArgumentException("[pandorical] " + id + "'s vanilla fallback is not a vanilla type");
        }
        KINDS.put(type, registration);
        Pandorical.LOGGER.info("Registered entity {} {}{}", id,
            registration.hasOwnBody() ? "with its own body" : "on " + BuiltInRegistries.ENTITY_TYPE.getKey(registration.getBase()),
            fallback != null ? ", shown to vanilla clients as " + BuiltInRegistries.ENTITY_TYPE.getKey(fallback) : "");
    }

    private static boolean vanilla(EntityType<?> type) {
        return "minecraft".equals(BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace());
    }

    public static boolean any() {
        return KINDS.values().stream().anyMatch(kind -> !kind.hasOwnBody());
    }

    public static boolean anyBodies() {
        return !LAYOUTS.isEmpty();
    }

    public static @Nullable EntityRegistration of(EntityType<?> type) {
        return KINDS.isEmpty() ? null : KINDS.get(type);
    }

    /** Whether the type is drawn from its own body, which is sent with every value it syncs. */
    public static boolean isBody(EntityType<?> type) {
        EntityRegistration kind = of(type);
        return kind != null && kind.hasOwnBody();
    }

    public static EntityKindsConfigS2C payload() {
        List<EntityKindsConfigS2C.Entry> entries = new ArrayList<>();
        KINDS.forEach((type, kind) -> {
            if (kind.hasOwnBody()) return;
            entries.add(new EntityKindsConfigS2C.Entry(
                BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(),
                BuiltInRegistries.ENTITY_TYPE.getKey(kind.getBase()).toString(),
                text(kind.getModel()), text(kind.getTexture()), text(kind.getBabyTexture()),
                kind.getDrawScale()));
        });
        return new EntityKindsConfigS2C(entries);
    }

    public static EntityBodiesConfigS2C bodiesPayload() {
        List<EntityBodiesConfigS2C.Entry> entries = new ArrayList<>();
        LAYOUTS.forEach((type, layout) -> {
            EntityRegistration kind = KINDS.get(type);
            var dimensions = type.getDimensions();
            entries.add(new EntityBodiesConfigS2C.Entry(
                BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(), layout.tier(), layout.serializers(),
                dimensions.width(), dimensions.height(), dimensions.eyeHeight(), type.getCategory().name(),
                type.clientTrackingRange(), type.updateInterval(), type.fireImmune(),
                text(kind.getModel()), text(kind.getTexture()), kind.getDrawScale()));
        });
        return new EntityBodiesConfigS2C(entries);
    }

    private static String text(@Nullable Identifier id) {
        return id == null ? "" : id.toString();
    }

    /** Set in the configuration phase: which of the two it was told, kinds on a base and bodies. */
    public static void understood(UUID profile, boolean kinds, boolean bodies) {
        if (kinds) UNDERSTOOD.add(profile);
        else UNDERSTOOD.remove(profile);
        if (bodies) UNDERSTOOD_BODIES.add(profile);
        else UNDERSTOOD_BODIES.remove(profile);
    }

    /** Told both, or neither. */
    public static void understood(UUID profile, boolean told) {
        understood(profile, told, told);
    }

    public static void forget(UUID profile) {
        UNDERSTOOD.remove(profile);
        UNDERSTOOD_BODIES.remove(profile);
    }

    /** Whether this player's client draws mod entities at all: was told the kinds on a base. */
    public static boolean understands(ServerPlayer player) {
        return UNDERSTOOD.contains(player.getUUID());
    }

    /** Whether this player's client also builds entities with their own bodies. */
    public static boolean understandsBodies(ServerPlayer player) {
        return UNDERSTOOD_BODIES.contains(player.getUUID());
    }

    private static boolean understands(ServerPlayer player, EntityType<?> type, EntityRegistration kind) {
        return kind.hasOwnBody() ? understandsBodies(player) && LAYOUTS.containsKey(type) : understands(player);
    }

    private static @Nullable EntityType<?> fallback(EntityType<?> type, EntityRegistration kind) {
        return UNREADABLE_FALLBACK.contains(type) ? null : kind.getVanillaFallback();
    }

    /** Whether the entity may be sent to the player at all. */
    public static boolean mayShow(Entity entity, ServerPlayer player) {
        EntityRegistration kind = of(entity.getType());
        return kind == null || understands(player, entity.getType(), kind) || fallback(entity.getType(), kind) != null;
    }

    /** The add packet this player can read: the real one, or the fallback in its place for a client that cannot. */
    public static ClientboundAddEntityPacket forPlayer(ClientboundAddEntityPacket packet, ServerPlayer player) {
        EntityRegistration kind = of(packet.getType());
        if (kind == null || understands(player, packet.getType(), kind)) return packet;
        EntityType<?> fallback = fallback(packet.getType(), kind);
        if (fallback == null) return packet;
        return new ClientboundAddEntityPacket(packet.getId(), packet.getUUID(), packet.getX(), packet.getY(),
            packet.getZ(), packet.getXRot(), packet.getYRot(), fallback, packet.getData(),
            packet.getMovement(), packet.getYHeadRot());
    }

    /**
     * Every value a body syncs, defaults included, for {@code getNonDefaultValues}: the client's
     * generic class has no idea what the entity's own values start at, so it is told them all.
     */
    public static List<SynchedEntityData.DataValue<?>> allValues(SynchedEntityData data) {
        List<SynchedEntityData.DataValue<?>> values = new ArrayList<>();
        for (SynchedEntityData.DataItem<?> item : ((SynchedEntityDataAccessor) data).pandorical$itemsById()) {
            values.add(item.value());
        }
        return values;
    }

    public static void registerCheck() {
        ServerLifecycleEvents.SERVER_STARTED.register(EntityKinds::readBodies);
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            EntityRegistration kind = of(entity.getType());
            if (kind == null || kind.hasOwnBody() || !CHECKED.add(entity.getType())) return;
            checkBase(entity, kind);
        });
    }

    /**
     * Each body's layout, off one made and never added, and whether its fallback can read it. Here
     * rather than at registration, which is too early to make an entity.
     */
    private static void readBodies(MinecraftServer server) {
        LAYOUTS.clear();
        UNREADABLE_FALLBACK.clear();
        KINDS.forEach((type, kind) -> {
            if (!kind.hasOwnBody()) return;
            Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            Entity made = type.create(server.overworld(), EntitySpawnReason.LOAD);
            if (!(made instanceof Mob mob)) {
                Pandorical.LOGGER.error("[pandorical] {} has its own body but is a {}, not a Mob: "
                    + "Pandorical clients are not sent it", id, made == null ? "nothing" : made.getClass().getSimpleName());
                return;
            }
            List<Integer> layout = layout(mob);
            LAYOUTS.put(type, new Layout(mob instanceof AgeableMob ? TIER_AGEABLE : TIER_MOB, layout));

            EntityType<?> fallback = kind.getVanillaFallback();
            if (fallback == null) return;
            Entity stand = fallback.create(server.overworld(), EntitySpawnReason.LOAD);
            List<Integer> theirs = stand == null ? List.of() : layout(stand);
            if (theirs.size() < layout.size() || !theirs.subList(0, layout.size()).equals(layout)) {
                UNREADABLE_FALLBACK.add(type);
                Pandorical.LOGGER.error("[pandorical] {} syncs data its fallback {} cannot read: vanilla clients are not "
                    + "shown it. A fallback has to sync at least what the entity does", id,
                    BuiltInRegistries.ENTITY_TYPE.getKey(fallback));
            }
        });
    }

    private static List<Integer> layout(Entity entity) {
        List<Integer> ids = new ArrayList<>();
        for (SynchedEntityData.DataItem<?> item : ((SynchedEntityDataAccessor) entity.getEntityData()).pandorical$itemsById()) {
            ids.add(EntityDataSerializers.getSerializedId(item.getAccessor().serializer()));
        }
        return List.copyOf(ids);
    }

    /**
     * The contract for a kind on a base, checked on the first of each type to load: its class
     * extends the base's, and its synced data is the base's. A break here garbles the entity on
     * every client, so it is said at error level rather than discovered as a client crash.
     */
    private static void checkBase(Entity entity, EntityRegistration kind) {
        Entity base = kind.getBase().create(entity.level(), EntitySpawnReason.LOAD);
        if (base == null) return;
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (!base.getClass().isInstance(entity)) {
            Pandorical.LOGGER.error("[pandorical] {} is a {}, which does not extend its base's {}: "
                + "clients will draw it wrong", id, entity.getClass().getSimpleName(), base.getClass().getSimpleName());
        }
        int own = ((SynchedEntityDataAccessor) entity.getEntityData()).pandorical$itemsById().length;
        int theirs = ((SynchedEntityDataAccessor) base.getEntityData()).pandorical$itemsById().length;
        if (own != theirs) {
            Pandorical.LOGGER.error("[pandorical] {} syncs {} data values where its base syncs {}: "
                + "clients decode its data by the base's layout, so this will garble or crash them", id, own, theirs);
        }
        base.discard();
    }
}
