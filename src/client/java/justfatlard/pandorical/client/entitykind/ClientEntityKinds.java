package justfatlard.pandorical.client.entitykind;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.client.mixin.EntityTypeAccessor;
import justfatlard.pandorical.protocol.EntityKindsConfigS2C;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The server's registered entities, as this client stands them in: each is its base underneath,
 * built by the base's own factory, sized as the base, drawn by the base's renderer, and dressed
 * with the type's model and skin.
 *
 * <p>Told in the configuration phase, before the content sync registers the stand-in types, so
 * they can be built from the base as they are registered.
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

    private static final Map<String, Kind> KINDS = new ConcurrentHashMap<>();

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

    /** The stand-in itself: the base's class, built for the stand-in's type. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static @Nullable Entity create(Kind kind, EntityType<?> type, Level level) {
        EntityType.EntityFactory factory = ((EntityTypeAccessor) kind.base()).pandorical$factory();
        return (Entity) factory.create(type, level);
    }

    public static void clear() {
        KINDS.clear();
    }
}
