package justfatlard.pandorical.client.entitykind;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.client.mixin.EntityTypeAccessor;
import justfatlard.pandorical.entitykind.EntityKinds;
import justfatlard.pandorical.mixin.SynchedEntityDataAccessor;
import justfatlard.pandorical.protocol.EntityBodiesConfigS2C;
import justfatlard.pandorical.protocol.EntityKindsConfigS2C;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The server's registered entities, as this client stands them in.
 *
 * <p>A kind on a base is its base underneath, built by the base's own factory, sized as the base,
 * drawn by the base's renderer, and dressed with the type's model and skin. A body is one of
 * Pandorical's generic mobs, holding whatever values the entity syncs, sized as the server says
 * and drawn from its model by {@link BodyRenderer}.
 *
 * <p>Told in the configuration phase, before the content sync registers the stand-in types, so
 * they can be built as they are registered.
 */
public final class ClientEntityKinds {
    private ClientEntityKinds() {}

    public record Kind(EntityType<?> base, @Nullable Identifier model, @Nullable Identifier texture,
            @Nullable Identifier babyTexture, float drawScale) {

        /** The skin for this entity as it is now: a baby is drawn with a model of its own. */
        public @Nullable Identifier textureFor(LivingEntity entity) {
            return entity.isBaby() && babyTexture != null ? babyTexture : texture;
        }
    }

    /** {@code serializers} in id order: every value the entity syncs. */
    public record Body(String tier, List<EntityDataSerializer<?>> serializers, float width, float height,
            float eyeHeight, MobCategory category, int trackingRange, int updateInterval, boolean fireImmune,
            Identifier model, @Nullable Identifier texture, float drawScale) {}

    private static final Map<String, Kind> KINDS = new ConcurrentHashMap<>();
    private static final Map<String, Body> BODIES = new ConcurrentHashMap<>();
    /** Bodies already found not to match the class they were built as, so it is said once each. */
    private static final Set<String> MISBUILT = ConcurrentHashMap.newKeySet();

    /** The body a generic mob is being built as, read while its synced data is defined. */
    private static final ThreadLocal<Body> BUILDING = new ThreadLocal<>();

    public static void receive(EntityKindsConfigS2C payload) {
        KINDS.clear();
        for (EntityKindsConfigS2C.Entry entry : payload.kinds()) {
            Identifier baseId = Identifier.tryParse(entry.base());
            EntityType<?> base = baseId == null ? null : BuiltInRegistries.ENTITY_TYPE.getValue(baseId);
            if (base == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(baseId)) {
                Pandorical.LOGGER.warn("Entity kind {} stands on {}, which this client does not have", entry.type(), entry.base());
                continue;
            }
            KINDS.put(entry.type(), new Kind(base, id(entry.model()), id(entry.texture()), id(entry.babyTexture()),
                entry.drawScale()));
        }
        Pandorical.LOGGER.info("Config phase: {} entity kind(s) to stand in", KINDS.size());
    }

    public static void receive(EntityBodiesConfigS2C payload) {
        BODIES.clear();
        MISBUILT.clear();
        for (EntityBodiesConfigS2C.Entry entry : payload.bodies()) {
            Body body = body(entry);
            if (body != null) BODIES.put(entry.type(), body);
        }
        Pandorical.LOGGER.info("Config phase: {} entity bodies to build", BODIES.size());
    }

    private static @Nullable Body body(EntityBodiesConfigS2C.Entry entry) {
        Identifier model = id(entry.model());
        if (model == null) {
            Pandorical.LOGGER.warn("Entity {} has no model to draw it with", entry.type());
            return null;
        }
        List<EntityDataSerializer<?>> serializers = new java.util.ArrayList<>();
        for (int serializerId : entry.layout()) {
            EntityDataSerializer<?> serializer = EntityDataSerializers.getSerializer(serializerId);
            if (serializer == null) {
                Pandorical.LOGGER.warn("Entity {} syncs a value of a kind this client does not know ({})", entry.type(), serializerId);
                return null;
            }
            serializers.add(serializer);
        }
        MobCategory category;
        try {
            category = MobCategory.valueOf(entry.category());
        } catch (IllegalArgumentException e) {
            category = MobCategory.MISC;
        }
        return new Body(entry.tier(), List.copyOf(serializers), entry.width(), entry.height(), entry.eyeHeight(),
            category, entry.trackingRange(), entry.updateInterval(), entry.fireImmune(), model, id(entry.texture()),
            entry.drawScale());
    }

    private static @Nullable Identifier id(String text) {
        return text.isEmpty() ? null : Identifier.tryParse(text);
    }

    public static @Nullable Kind get(String typeId) {
        return KINDS.get(typeId);
    }

    public static @Nullable Kind of(EntityType<?> type) {
        if (KINDS.isEmpty()) return null;
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return id == null ? null : KINDS.get(id.toString());
    }

    public static @Nullable Body body(String typeId) {
        return BODIES.get(typeId);
    }

    public static @Nullable Body bodyOf(EntityType<?> type) {
        if (BODIES.isEmpty()) return null;
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return id == null ? null : BODIES.get(id.toString());
    }

    /** The stand-in itself: the base's class, built for the stand-in's type. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static @Nullable Entity create(Kind kind, EntityType<?> type, Level level) {
        EntityType.EntityFactory factory = ((EntityTypeAccessor) kind.base()).pandorical$factory();
        return (Entity) factory.create(type, level);
    }

    /**
     * A body: the generic mob of its tier, holding every value the server says it syncs. Null when
     * the values the class defines itself are not the ones the server leads with, which a server and
     * client from different game versions could disagree on; the spawn is then skipped rather than
     * the entity read wrong.
     */
    @SuppressWarnings("unchecked")
    public static @Nullable Mob create(Body body, EntityType<?> type, Level level) {
        Mob mob;
        BUILDING.set(body);
        try {
            mob = EntityKinds.TIER_AGEABLE.equals(body.tier())
                ? new AgeableBodyMob((EntityType<? extends AgeableBodyMob>) type, level)
                : new BodyMob((EntityType<? extends BodyMob>) type, level);
        } finally {
            BUILDING.remove();
        }
        SynchedEntityData.DataItem<?>[] items = ((SynchedEntityDataAccessor) mob.getEntityData()).pandorical$itemsById();
        boolean matches = items.length == body.serializers().size();
        for (int i = 0; matches && i < items.length; i++) {
            matches = items[i].getAccessor().serializer() == body.serializers().get(i);
        }
        if (matches) return mob;
        String id = String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(type));
        if (MISBUILT.add(id)) {
            Pandorical.LOGGER.warn("Entity {} syncs its values in an order this client's {} mob does not: not drawn",
                id, body.tier());
        }
        return null;
    }

    /** The body being built on this thread, while its synced data is defined; null otherwise. */
    public static @Nullable Body building() {
        return BUILDING.get();
    }

    public static void clear() {
        KINDS.clear();
        BODIES.clear();
        MISBUILT.clear();
    }
}
