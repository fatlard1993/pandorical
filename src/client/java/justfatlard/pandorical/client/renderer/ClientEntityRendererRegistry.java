package justfatlard.pandorical.client.renderer;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.api.NotUnderstood;
import justfatlard.pandorical.client.ClientNotices;
import justfatlard.pandorical.api.EntityRendererRegistry;
import justfatlard.pandorical.protocol.EntityRenderersS2C;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
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

/** {@link EntityRenderers#register} is private, so this writes {@code PROVIDERS} by reflection. */
@Environment(EnvType.CLIENT)
public final class ClientEntityRendererRegistry {
    private ClientEntityRendererRegistry() {}

    private static final Set<String> registeredTypes = ConcurrentHashMap.newKeySet();

    /** Read by the stub EntityTypes at spawn, long after this packet, to pick the client entity. */
    private static final Map<String, String> rendererKeys = new ConcurrentHashMap<>();

    public static String getRendererKey(String typeId) {
        return rendererKeys.get(typeId);
    }

    @SuppressWarnings("rawtypes")
    private static volatile Map providers = null;

    /** Render thread only. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void applyRenderers(EntityRenderersS2C packet) {
        Map providersMap = getProvidersMap();
        if (providersMap == null) {
            Pandorical.LOGGER.error("[pandorical] Cannot register entity renderers — PROVIDERS map is inaccessible");
            return;
        }

        boolean addedAny = false;

        for (Map.Entry<String, String> entry : packet.renderers().entrySet()) {
            String typeId = entry.getKey();
            String rendererKey = entry.getValue();

            rendererKeys.put(typeId, rendererKey);

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
                ClientNotices.report(NotUnderstood.RENDERER_KEY, rendererKey);
                continue;
            }

            providersMap.put(entityType, providerFactory);
            registeredTypes.add(typeId);
            addedAny = true;
            Pandorical.LOGGER.debug("[pandorical] Registered client renderer '{}' for entity type '{}'",
                rendererKey, typeId);
        }

        // The dispatcher copies PROVIDERS at resource reload, which usually runs before this
        // packet after a join; without a rebuild the new types render with a null renderer (NPE).
        if (addedAny) {
            Minecraft mc = Minecraft.getInstance();
            mc.getEntityRenderDispatcher().onResourceManagerReload(mc.getResourceManager());
            Pandorical.LOGGER.debug("[pandorical] Rebuilt entity renderer dispatcher for synced types");
        }
    }

    public static void reset() {
        registeredTypes.clear();
        rendererKeys.clear();
        // Not the providers map: it holds vanilla's renderers too.
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static EntityRendererProvider<?> resolveProvider(String key) {
        return switch (key) {
            case EntityRendererRegistry.KEY_THROWN_ITEM ->
                // Raw: T extends Entity & ItemSupplier has no wildcard form.
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
