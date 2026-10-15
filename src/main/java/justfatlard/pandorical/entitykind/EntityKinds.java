package justfatlard.pandorical.entitykind;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.api.EntityRegistration;
import justfatlard.pandorical.mixin.SynchedEntityDataAccessor;
import justfatlard.pandorical.protocol.EntityKindsConfigS2C;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
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
 * read off the play handshake, which lands after the first entities have already been paired.
 */
public final class EntityKinds {
    private EntityKinds() {}

    private static final Map<EntityType<?>, EntityRegistration> KINDS = new ConcurrentHashMap<>();
    private static final Set<UUID> UNDERSTOOD = ConcurrentHashMap.newKeySet();
    /** Types already checked against their base, so the check runs once each. */
    private static final Set<EntityType<?>> CHECKED = ConcurrentHashMap.newKeySet();

    public static void register(EntityType<?> type, EntityRegistration registration) {
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (BuiltInRegistries.ENTITY_TYPE.getResourceKey(type).isEmpty()) {
            throw new IllegalStateException("[pandorical] registerEntity before the entity type is registered");
        }
        if (registration.getBase() == null) {
            throw new IllegalArgumentException("[pandorical] " + id + " has no base: the client needs one to stand in");
        }
        if (!"minecraft".equals(BuiltInRegistries.ENTITY_TYPE.getKey(registration.getBase()).getNamespace())) {
            throw new IllegalArgumentException("[pandorical] " + id + "'s base must be a vanilla type, so every client has it");
        }
        KINDS.put(type, registration);
        Pandorical.LOGGER.info("Registered entity {} on {}{}", id,
            BuiltInRegistries.ENTITY_TYPE.getKey(registration.getBase()),
            registration.hasVanillaFallback() ? ", shown to vanilla clients as it" : "");
    }

    public static boolean any() {
        return !KINDS.isEmpty();
    }

    public static @Nullable EntityRegistration of(EntityType<?> type) {
        return KINDS.isEmpty() ? null : KINDS.get(type);
    }

    public static EntityKindsConfigS2C payload() {
        List<EntityKindsConfigS2C.Entry> entries = new ArrayList<>();
        KINDS.forEach((type, kind) -> entries.add(new EntityKindsConfigS2C.Entry(
            BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(),
            BuiltInRegistries.ENTITY_TYPE.getKey(kind.getBase()).toString(),
            text(kind.getModel()), text(kind.getTexture()), text(kind.getBabyTexture()),
            kind.getDrawScale())));
        return new EntityKindsConfigS2C(entries);
    }

    private static String text(@Nullable Identifier id) {
        return id == null ? "" : id.toString();
    }

    /** Set in the configuration phase: told the kinds, or not. */
    public static void understood(UUID profile, boolean told) {
        if (told) UNDERSTOOD.add(profile);
        else UNDERSTOOD.remove(profile);
    }

    public static void forget(UUID profile) {
        UNDERSTOOD.remove(profile);
    }

    /** Whether this player's client was told the kinds and built stand-ins for them. */
    public static boolean understands(ServerPlayer player) {
        return UNDERSTOOD.contains(player.getUUID());
    }

    /** Whether the entity may be sent to the player at all. */
    public static boolean mayShow(Entity entity, ServerPlayer player) {
        EntityRegistration kind = of(entity.getType());
        return kind == null || kind.hasVanillaFallback() || understands(player);
    }

    /** The add packet this player can read: the real one, or the base in its place for a vanilla client. */
    public static ClientboundAddEntityPacket forPlayer(ClientboundAddEntityPacket packet, ServerPlayer player) {
        EntityRegistration kind = of(packet.getType());
        if (kind == null || understands(player)) return packet;
        return new ClientboundAddEntityPacket(packet.getId(), packet.getUUID(), packet.getX(), packet.getY(),
            packet.getZ(), packet.getXRot(), packet.getYRot(), kind.getBase(), packet.getData(),
            packet.getMovement(), packet.getYHeadRot());
    }

    /**
     * The contract, checked on the first of each type to load: its class extends the base's, and
     * its synced data is the base's. A break here garbles the entity on every client, so it is
     * said at error level rather than discovered as a client crash.
     */
    public static void registerCheck() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            EntityRegistration kind = of(entity.getType());
            if (kind == null || !CHECKED.add(entity.getType())) return;
            Entity base = kind.getBase().create(level, EntitySpawnReason.LOAD);
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
        });
    }
}
