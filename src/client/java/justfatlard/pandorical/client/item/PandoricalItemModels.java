package justfatlard.pandorical.client.item;

import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Item models only a Pandorical client can read. A mod ships its plain model at
 * {@code assets/<ns>/items/<name>.json}, which every client loads, and the one that needs
 * Pandorical at {@code assets/<ns>/pandorical/items/<name>.json}, which nothing but this
 * client looks at; this client loads the second in place of the first. So an older client keeps
 * the plain model rather than failing on a model type it has never heard of.
 *
 * <p>A model type newer than that folder needs a folder newer clients alone read, or a client
 * that reads the folder but not the type would fail on it: {@code pandorical/items_v2/} for
 * {@code pandorical:picture}, preferred over {@code pandorical/items/} where both are shipped.
 */
public final class PandoricalItemModels {
    private PandoricalItemModels() {}

    public static final Identifier CELLS = Identifier.fromNamespaceAndPath("pandorical", "cells");
    public static final Identifier PICTURE = Identifier.fromNamespaceAndPath("pandorical", "picture");

    /** Newest first. */
    private static final List<String> FOLDERS = List.of("pandorical/items_v2/", "pandorical/items/");

    private static volatile boolean registered;

    public static void register() {
        SpecialModelRenderers.ID_MAPPER.put(CELLS, CellsItemRenderer.Unbaked.MAP_CODEC);
        SpecialModelRenderers.ID_MAPPER.put(PICTURE, PictureItemRenderer.Unbaked.MAP_CODEC);
        registered = true;
    }

    /** {@code items/} listing in, the same listing with each Pandorical model in its plain one's place. */
    public static Map<Identifier, Resource> overlay(ResourceManager manager, Map<Identifier, Resource> listed) {
        if (!registered) return listed;
        Map<Identifier, Resource> out = null;
        for (Map.Entry<Identifier, Resource> entry : listed.entrySet()) {
            Identifier file = entry.getKey();
            for (String folder : FOLDERS) {
                var found = manager.getResource(file.withPath(path -> folder + path.substring("items/".length())));
                if (found.isEmpty()) continue;
                if (out == null) out = new HashMap<>(listed);
                out.put(file, found.get());
                break;
            }
        }
        return out == null ? listed : out;
    }
}
