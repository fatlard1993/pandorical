package justfatlard.pandorical.client.content;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;

/**
 * In-memory resource pack that serves assets synced from the server.
 * Injected into the client's resource manager to provide models/textures
 * for dynamically registered blocks and items.
 */
public class VirtualResourcePack implements PackResources {
    private static final String PACK_ID = "pandorical_virtual";

    private final Map<Identifier, byte[]> resources = new HashMap<>();

    /**
     * Add a resource. Path format: "assets/{namespace}/{type}/{name}"
     * e.g., "assets/big-boats/models/block/helm.json"
     */
    public void addResource(String path, byte[] data) {
        if (path.startsWith("assets/")) {
            String withoutPrefix = path.substring(7);
            int slash = withoutPrefix.indexOf('/');
            if (slash > 0) {
                String namespace = withoutPrefix.substring(0, slash);
                String rest = withoutPrefix.substring(slash + 1);
                resources.put(Identifier.fromNamespaceAndPath(namespace, rest), data);
                return;
            }
        }
        justfatlard.pandorical.Pandorical.LOGGER.warn(
            "Skipped asset with invalid path format: '{}' (expected 'assets/{{namespace}}/...')", path);
    }

    public boolean hasResources() {
        return !resources.isEmpty();
    }

    /** Dump lang files in the pack for debugging. */
    public void debugLangFiles() {
        for (var entry : resources.entrySet()) {
            if (entry.getKey().getPath().contains("lang")) {
                justfatlard.pandorical.Pandorical.LOGGER.info("VirtualPack lang resource: {} ({} bytes)",
                    entry.getKey(), entry.getValue().length);
            }
        }
    }

    public void clear() {
        resources.clear();
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... path) {
        return null;
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType packType, Identifier id) {
        if (packType != PackType.CLIENT_RESOURCES) return null;
        byte[] data = resources.get(id);
        if (data == null) return null;
        if (id.getPath().contains("lang")) {
            justfatlard.pandorical.Pandorical.LOGGER.info("VirtualPack getResource HIT: {}", id);
        }
        return () -> new ByteArrayInputStream(data);
    }

    @Override
    public void listResources(PackType packType, String namespace, String path,
                               ResourceOutput output) {
        if (packType != PackType.CLIENT_RESOURCES) return;
        int found = 0;
        for (var entry : resources.entrySet()) {
            Identifier id = entry.getKey();
            if (id.getNamespace().equals(namespace) && id.getPath().startsWith(path)) {
                output.accept(id, () -> new ByteArrayInputStream(entry.getValue()));
                found++;
            }
        }
        if (path.contains("lang") && found > 0) {
            justfatlard.pandorical.Pandorical.LOGGER.info("VirtualPack listResources ns={} path={} found={}", namespace, path, found);
        }
    }

    @Override
    public Set<String> getNamespaces(PackType packType) {
        if (packType != PackType.CLIENT_RESOURCES) return Set.of();
        Set<String> namespaces = new HashSet<>();
        for (Identifier id : resources.keySet()) {
            namespaces.add(id.getNamespace());
        }
        return namespaces;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getMetadataSection(MetadataSectionType<T> type) {
        // Only return metadata for pack metadata section types
        try {
            var packMetaClass = net.minecraft.server.packs.metadata.pack.PackMetadataSection.class;
            // Check if the requested type matches any PackMetadataSection type
            boolean isPackMeta = false;
            for (var field : packMetaClass.getDeclaredFields()) {
                if (net.minecraft.server.packs.metadata.MetadataSectionType.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    if (field.get(null) == type) {
                        isPackMeta = true;
                        break;
                    }
                }
            }
            if (isPackMeta) {
                var ctors = packMetaClass.getDeclaredConstructors();
                for (var ctor : ctors) {
                    if (ctor.getParameterCount() == 2) {
                        ctor.setAccessible(true);
                        var desc = net.minecraft.network.chat.Component.literal("Pandorical synced assets");
                        var range = new net.minecraft.util.InclusiveRange<>(46, 46);
                        return (T) ctor.newInstance(desc, range);
                    }
                }
            }
        } catch (Exception e) {
            // Fall through
        }
        return null;
    }

    @Override
    public PackLocationInfo location() {
        return new PackLocationInfo(PACK_ID,
            net.minecraft.network.chat.Component.literal("Pandorical Virtual Assets"),
            net.minecraft.server.packs.repository.PackSource.BUILT_IN,
            Optional.empty());
    }

    @Override
    public void close() {
        // No-op — in-memory pack
    }
}
