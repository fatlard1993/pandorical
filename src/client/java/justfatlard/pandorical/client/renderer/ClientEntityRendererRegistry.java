package justfatlard.pandorical.client.renderer;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.api.EntityRendererRegistry;
import justfatlard.pandorical.protocol.EntityRenderersS2C;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side handler for {@link EntityRenderersS2C} packets.
 *
 * <p>Since {@link EntityRenderers#register} is private, this class inserts entries directly
 * into the {@code PROVIDERS} static map via reflection. This is analogous to how modding
 * frameworks (including Fabric's own internals) handle dynamic renderer registration at
 * points other than class initialisation.
 *
 * <p>Only renderer keys defined in {@link EntityRendererRegistry} are supported.
 */
@Environment(EnvType.CLIENT)
public final class ClientEntityRendererRegistry {
    private ClientEntityRendererRegistry() {}

    /** Tracks which entity type IDs we have already registered to avoid duplicates. */
    private static final Set<String> registeredTypes = ConcurrentHashMap.newKeySet();

    /** Cached reflection access to {@code EntityRenderers.PROVIDERS}. */
    @SuppressWarnings("rawtypes")
    private static volatile Map providers = null;

    /**
     * Apply all renderer mappings from the packet. Called on the render thread.
     *
     * @param packet the received packet containing entity type id → renderer key pairs
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void applyRenderers(EntityRenderersS2C packet) {
        Map providersMap = getProvidersMap();
        if (providersMap == null) {
            Pandorical.LOGGER.error("[pandorical] Cannot register entity renderers — PROVIDERS map is inaccessible");
            return;
        }

        for (Map.Entry<String, String> entry : packet.renderers().entrySet()) {
            String typeId = entry.getKey();
            String rendererKey = entry.getValue();

            if (registeredTypes.contains(typeId)) {
                Pandorical.LOGGER.debug("[pandorical] Skipping already-registered entity renderer for '{}'", typeId);
                continue;
            }

            Identifier id = Identifier.tryParse(typeId);
            if (id == null) {
                Pandorical.LOGGER.warn("[pandorical] Ignoring invalid entity type id: '{}'", typeId);
                continue;
            }

            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                Pandorical.LOGGER.warn("[pandorical] Unknown entity type '{}' — cannot register renderer", typeId);
                continue;
            }

            EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getValue(id);
            EntityRendererProvider<?> providerFactory = resolveProvider(rendererKey);
            if (providerFactory == null) {
                Pandorical.LOGGER.warn("[pandorical] Unknown renderer key '{}' for entity type '{}' — skipping",
                    rendererKey, typeId);
                continue;
            }

            providersMap.put(entityType, providerFactory);
            registeredTypes.add(typeId);
            Pandorical.LOGGER.debug("[pandorical] Registered client renderer '{}' for entity type '{}'",
                rendererKey, typeId);
        }
    }

    /** Clear state on disconnect so re-joining re-registers correctly. */
    public static void reset() {
        registeredTypes.clear();
        // Do NOT clear the providers map itself — that would break vanilla renderers.
    }

    // --- Private helpers ---

    /**
     * Resolve a renderer key to an {@link EntityRendererProvider}.
     * Returns {@code null} for unknown keys.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static EntityRendererProvider<?> resolveProvider(String key) {
        return switch (key) {
            case EntityRendererRegistry.KEY_THROWN_ITEM ->
                // Raw cast is intentional — we cannot express T extends Entity & ItemSupplier
                // in the provider map's wildcard-typed signature. This is safe at runtime.
                (EntityRendererProvider) ctx -> new ThrownItemRenderer(ctx);
            case EntityRendererRegistry.KEY_INVISIBLE ->
                (EntityRendererProvider) ctx -> new NoopRenderer<>(ctx);
            default -> null;
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map getProvidersMap() {
        if (providers != null) return providers;
        try {
            Field field = EntityRenderers.class.getDeclaredField("PROVIDERS");
            field.setAccessible(true);
            providers = (Map) field.get(null);
            return providers;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Pandorical.LOGGER.error("[pandorical] Failed to access EntityRenderers.PROVIDERS via reflection: {}", e.getMessage());
            return null;
        }
    }
}
