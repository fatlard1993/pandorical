package justfatlard.pandorical.login;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.content.ContentRegistry;
import justfatlard.pandorical.protocol.AssetsOfferConfigS2C;
import justfatlard.pandorical.protocol.SyncAssetsConfigS2C;
import justfatlard.pandorical.protocol.SyncContentConfigS2C;
import justfatlard.pandorical.protocol.SyncContentS2C;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

public class PandoricalSyncTask implements ConfigurationTask {
    public static final Type TYPE = new Type("pandorical:sync_content");

    private final List<SyncContentS2C.BlockEntry> blocks;
    private final List<SyncContentS2C.ItemEntry> items;
    private final ServerConfigurationPacketListenerImpl handler;
    private final ContentRegistry.ConfigAssets assets;
    private final List<String> entityTypes;
    private final List<String> blockEntityTypes;
    private final List<String> villagerProfessions;
    private final List<String> poiTypes;
    private final List<String> menuTypes;
    private final List<String> recipeBookCategories;
    private final boolean solidRails;
    /** Null when the client cannot take it or there are none. */
    private final justfatlard.pandorical.protocol.EntityKindsConfigS2C kinds;
    /** Null when the client cannot take it or there are none. */
    private final justfatlard.pandorical.protocol.EntityBodiesConfigS2C bodies;

    /** Logins offered the assets by hash whose answer is still out, with the chunks it may ask for. */
    private static final Map<ServerConfigurationPacketListenerImpl, List<SyncAssetsConfigS2C>> offered =
        Collections.synchronizedMap(new WeakHashMap<>());

    public PandoricalSyncTask(
            ServerConfigurationPacketListenerImpl handler,
            List<SyncContentS2C.BlockEntry> blocks,
            List<SyncContentS2C.ItemEntry> items,
            ContentRegistry.ConfigAssets assets,
            List<String> entityTypes,
            List<String> blockEntityTypes,
            List<String> villagerProfessions,
            List<String> poiTypes,
            List<String> menuTypes,
            List<String> recipeBookCategories, boolean solidRails,
            justfatlard.pandorical.protocol.EntityKindsConfigS2C kinds,
            justfatlard.pandorical.protocol.EntityBodiesConfigS2C bodies) {
        this.blocks = blocks;
        this.items = items;
        this.handler = handler;
        this.assets = assets;
        this.entityTypes = entityTypes;
        this.blockEntityTypes = blockEntityTypes;
        this.villagerProfessions = villagerProfessions;
        this.poiTypes = poiTypes;
        this.menuTypes = menuTypes;
        this.recipeBookCategories = recipeBookCategories;
        this.solidRails = solidRails;
        this.kinds = kinds;
        this.bodies = bodies;
    }

    /**
     * Well under vanilla's 8 MiB packet cap: this counts entries only, and the stub lists and
     * framing ride on top.
     */
    private static final int CHUNK_BUDGET_BYTES = 4 * 1024 * 1024;

    /**
     * Chunks by encoded size, since one entry can outweigh hundreds of others. An entry over the
     * budget alone still gets a chunk of its own.
     */
    private static <T> List<List<T>> intoChunks(List<T> entries, StreamCodec<ByteBuf, T> codec) {
        List<List<T>> chunks = new ArrayList<>();
        List<T> current = new ArrayList<>();
        long size = 0;

        for (T entry : entries) {
            ByteBuf scratch = Unpooled.buffer();
            int encoded;
            try {
                codec.encode(scratch, entry);
                encoded = scratch.readableBytes();
            } finally {
                scratch.release();
            }

            if (!current.isEmpty() && size + encoded > CHUNK_BUDGET_BYTES) {
                chunks.add(current);
                current = new ArrayList<>();
                size = 0;
            }
            current.add(entry);
            size += encoded;
        }
        if (!current.isEmpty()) chunks.add(current);

        return chunks.isEmpty() ? List.of(List.of()) : chunks;
    }

    @Override
    public void start(Consumer<Packet<?>> sender) {
        List<SyncAssetsConfigS2C> assetChunks = assets.chunks();
        int expectedChunks = assetChunks.size();

        List<List<SyncContentS2C.BlockEntry>> blockChunks =
            intoChunks(blocks, SyncContentS2C.BlockEntry.STREAM_CODEC);
        List<List<SyncContentS2C.ItemEntry>> itemChunks =
            intoChunks(items, SyncContentS2C.ItemEntry.STREAM_CODEC);

        int total = Math.max(blockChunks.size(), itemChunks.size());

        // First: the client builds each entity type's stand-in from its kind as the content below
        // registers it.
        if (kinds != null) sender.accept(ServerConfigurationNetworking.createClientboundPacket(kinds));
        if (bodies != null) sender.accept(ServerConfigurationNetworking.createClientboundPacket(bodies));

        for (int i = 0; i < total; i++) {
            var contentPacket = new SyncContentConfigS2C(
                i < blockChunks.size() ? blockChunks.get(i) : List.of(),
                i < itemChunks.size() ? itemChunks.get(i) : List.of(),
                i, total, expectedChunks,
                entityTypes, blockEntityTypes, villagerProfessions, poiTypes, menuTypes,
                recipeBookCategories, solidRails);
            sender.accept(ServerConfigurationNetworking.createClientboundPacket(contentPacket));
        }

        Pandorical.LOGGER.info("Sending config-phase content sync: {} blocks, {} items in {} chunk(s), " +
            "{} entity types, {} block entity types, {} villager professions, {} POI types, " +
            "{} menu types, {} recipe book categories, {} asset chunks",
            blocks.size(), items.size(), total, entityTypes.size(), blockEntityTypes.size(),
            villagerProfessions.size(), poiTypes.size(), menuTypes.size(),
            recipeBookCategories.size(), expectedChunks);

        if (!assetChunks.isEmpty() && ServerConfigurationNetworking.canSend(handler, AssetsOfferConfigS2C.TYPE)) {
            offered.put(handler, assetChunks);
            sender.accept(ServerConfigurationNetworking.createClientboundPacket(
                new AssetsOfferConfigS2C(assets.sha256(), expectedChunks)));
            return;
        }
        assetChunks.forEach(chunk -> sender.accept(ServerConfigurationNetworking.createClientboundPacket(chunk)));
    }

    /** The client's answer to the offer: the chunks if it asked, nothing if it has them already. */
    public static void answered(ServerConfigurationPacketListenerImpl handler, boolean want) {
        List<SyncAssetsConfigS2C> chunks = offered.remove(handler);
        if (chunks == null) return;
        var profile = handler.getOwner();
        String name = profile != null ? profile.name() : "(unknown profile)";
        if (!want) {
            Pandorical.LOGGER.info("Client {} has these assets cached; sending none", name);
            return;
        }
        Pandorical.LOGGER.info("Client {} asked for the assets; sending {} chunk(s)", name, chunks.size());
        chunks.forEach(chunk -> ServerConfigurationNetworking.send(handler, chunk));
    }

    public static void forget(ServerConfigurationPacketListenerImpl handler) {
        offered.remove(handler);
    }

    @Override
    public Type type() {
        return TYPE;
    }
}
