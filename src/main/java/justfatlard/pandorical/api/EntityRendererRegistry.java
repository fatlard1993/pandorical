package justfatlard.pandorical.api;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side registry that maps entity types to renderer keys.
 * Populated via {@link PandoricalApi#registerEntityRenderer(EntityType, String)} during mod init.
 * The full map is sent to connecting clients via {@link justfatlard.pandorical.protocol.EntityRenderersS2C}.
 */
public final class EntityRendererRegistry {
    private EntityRendererRegistry() {}

    /**
     * Supported renderer keys.
     * "thrown_item" — ThrownItemRenderer (for thrown projectile items)
     * "invisible"   — NoopRenderer (renders nothing; useful for server-side-only logic entities)
     */
    public static final String KEY_THROWN_ITEM = "thrown_item";
    public static final String KEY_INVISIBLE = "invisible";

    private static final Set<String> VALID_KEYS = Set.of(KEY_THROWN_ITEM, KEY_INVISIBLE);

    // Map: entity type registry id string → renderer key
    private static final Map<String, String> registry = new ConcurrentHashMap<>();

    /**
     * Register an entity type with the given renderer key.
     * Must be called during server-side mod initialisation (before any players connect).
     *
     * @param entityType  the entity type to register a renderer for
     * @param rendererKey one of {@link #KEY_THROWN_ITEM} or {@link #KEY_INVISIBLE}
     * @throws IllegalArgumentException if {@code rendererKey} is not a recognised key
     */
    public static void register(EntityType<?> entityType, String rendererKey) {
        if (!VALID_KEYS.contains(rendererKey)) {
            throw new IllegalArgumentException(
                "[pandorical] Unknown renderer key '" + rendererKey + "'. Valid keys: " + VALID_KEYS);
        }
        Identifier id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        if (id == null) {
            throw new IllegalStateException(
                "[pandorical] EntityType is not registered — call registerEntityRenderer after registering the entity type");
        }
        registry.put(id.toString(), rendererKey);
    }

    /**
     * Returns an immutable snapshot of all registered entity type → renderer key mappings.
     * This is the payload sent in {@link justfatlard.pandorical.protocol.EntityRenderersS2C}.
     */
    public static Map<String, String> getAll() {
        return Collections.unmodifiableMap(registry);
    }
}
