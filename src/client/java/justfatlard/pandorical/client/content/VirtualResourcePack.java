package justfatlard.pandorical.client.content;

import justfatlard.pandorical.Pandorical;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.InclusiveRange;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VirtualResourcePack implements PackResources {
    private static final String PACK_ID = "pandorical_virtual";

    // A reload lists this on a worker thread while a sync or disconnect writes it on another.
    private final Map<Identifier, byte[]> resources = new ConcurrentHashMap<>();

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
        Pandorical.LOGGER.warn(
            "Skipped asset with invalid path format: '{}' (expected 'assets/{{namespace}}/...')", path);
    }

    public boolean hasResources() {
        return !resources.isEmpty();
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
            Pandorical.LOGGER.info("VirtualPack listResources ns={} path={} found={}", namespace, path, found);
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
        try {
            var packMetaClass = PackMetadataSection.class;
            boolean isPackMeta = false;
            for (var field : packMetaClass.getDeclaredFields()) {
                if (MetadataSectionType.class.isAssignableFrom(field.getType())) {
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
                        var desc = Component.literal("Pandorical synced assets");
                        var range = new InclusiveRange<>(46, 46);
                        return (T) ctor.newInstance(desc, range);
                    }
                }
            }
        } catch (Exception e) {
        }
        return null;
    }

    @Override
    public PackLocationInfo location() {
        return new PackLocationInfo(PACK_ID,
            Component.literal("Pandorical Virtual Assets"),
            PackSource.BUILT_IN,
            Optional.empty());
    }

    @Override
    public void close() {
    }
}
