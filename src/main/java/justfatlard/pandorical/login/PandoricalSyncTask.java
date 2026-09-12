package justfatlard.pandorical.login;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.protocol.SyncAssetsConfigS2C;
import justfatlard.pandorical.protocol.SyncContentConfigS2C;
import justfatlard.pandorical.protocol.SyncContentS2C;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ConfigurationTask;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class PandoricalSyncTask implements ConfigurationTask {
    public static final Type TYPE = new Type("pandorical:sync_content");

    private final List<SyncContentS2C.BlockEntry> blocks;
    private final List<SyncContentS2C.ItemEntry> items;
    private final List<SyncAssetsConfigS2C> assetChunks;
    private final List<String> entityTypes;
    private final List<String> blockEntityTypes;
    private final List<String> villagerProfessions;
    private final List<String> poiTypes;
    private final List<String> menuTypes;
    private final List<String> recipeBookCategories;
    private final boolean solidRails;

    public PandoricalSyncTask(
            List<SyncContentS2C.BlockEntry> blocks,
            List<SyncContentS2C.ItemEntry> items,
            List<SyncAssetsConfigS2C> assetChunks,
            List<String> entityTypes,
            List<String> blockEntityTypes,
            List<String> villagerProfessions,
            List<String> poiTypes,
            List<String> menuTypes,
            List<String> recipeBookCategories, boolean solidRails) {
        this.blocks = blocks;
        this.items = items;
        this.assetChunks = assetChunks;
        this.entityTypes = entityTypes;
        this.blockEntityTypes = blockEntityTypes;
        this.villagerProfessions = villagerProfessions;
        this.poiTypes = poiTypes;
        this.menuTypes = menuTypes;
        this.recipeBookCategories = recipeBookCategories;
        this.solidRails = solidRails;
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
        int expectedChunks = assetChunks.size();

        List<List<SyncContentS2C.BlockEntry>> blockChunks =
            intoChunks(blocks, SyncContentS2C.BlockEntry.STREAM_CODEC);
        List<List<SyncContentS2C.ItemEntry>> itemChunks =
            intoChunks(items, SyncContentS2C.ItemEntry.STREAM_CODEC);

        int total = Math.max(blockChunks.size(), itemChunks.size());

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

        for (SyncAssetsConfigS2C chunk : assetChunks) {
            sender.accept(ServerConfigurationNetworking.createClientboundPacket(chunk));
        }
    }

    @Override
    public Type type() {
        return TYPE;
    }
}
