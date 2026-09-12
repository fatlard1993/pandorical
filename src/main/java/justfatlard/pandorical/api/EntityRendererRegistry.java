package justfatlard.pandorical.api;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class EntityRendererRegistry {
    private EntityRendererRegistry() {}

    /** Vanilla's {@code ThrownItemRenderer}, for thrown projectile items. */
    public static final String KEY_THROWN_ITEM = "thrown_item";
    /** Draws nothing. */
    public static final String KEY_INVISIBLE = "invisible";

    private static final Set<String> VALID_KEYS = Set.of(KEY_THROWN_ITEM, KEY_INVISIBLE);

    private static final Map<String, String> registry = new ConcurrentHashMap<>();

    public static void register(EntityType<?> entityType, String rendererKey) {
        if (!VALID_KEYS.contains(rendererKey)) {
            throw new IllegalArgumentException(
                "[pandorical] Unknown renderer key '" + rendererKey + "'. Valid keys: " + VALID_KEYS);
        }
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        if (id == null) {
            throw new IllegalStateException(
                "[pandorical] EntityType is not registered — call registerEntityRenderer after registering the entity type");
        }
        registry.put(id.toString(), rendererKey);
    }

    public static Map<String, String> getAll() {
        return Collections.unmodifiableMap(registry);
    }
}
