package justfatlard.pandorical.config;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.protocol.SyncAssetsConfigS2C;
import justfatlard.pandorical.protocol.SyncContentConfigS2C;
import justfatlard.pandorical.protocol.SyncContentS2C;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ConfigurationTask;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Configuration-phase task that syncs Pandorical content (block/item definitions + assets)
 * to the client BEFORE Fabric's SynchronizeRegistriesTask runs.
 *
 * Flow:
 * 1. Server sends SyncContentConfigS2C with block/item definitions + other registry IDs
 * 2. Server sends SyncAssetsConfigS2C chunks with compressed assets
 * 3. Client registers blocks/items/stubs, loads assets, sends ContentReadyConfigC2S
 * 4. Server completes this task, allowing Fabric's registry sync to proceed
 *
 * Since blocks are registered on the client before Fabric's sync,
 * Fabric will see them and assign correct IDs. No more manual addMapping().
 */
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
     * How much encoded content one packet may carry.
     *
     * <p>Vanilla refuses a packet over 8 MiB. This sits well under it because the budget is
     * measured on the entries alone: the registry-stub lists, the framing and the varints all ride
     * on top, and a chunk that fitted exactly would be over the moment anything was added around
     * it. Headroom here is cheaper than a join that fails at the encoder.
     */
    private static final int CHUNK_BUDGET_BYTES = 4 * 1024 * 1024;

    /**
     * Split a list so no chunk's encoded size exceeds the budget.
     *
     * <p>Measured, not counted. Entries are wildly uneven - one block in this suite carries forty
     * thousand block states and encodes larger than several hundred ordinary ones together - so a
     * fixed number of entries per chunk would still produce packets over the limit, and would do it
     * unpredictably as mods come and go. Each entry is encoded once to see how big it actually is.
     *
     * <p>An entry too large for a whole chunk on its own still gets its own chunk. There is nothing
     * else to be done with it here, and one packet over the limit reports itself far better than a
     * silent truncation would.
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

        // Send asset chunks
        for (SyncAssetsConfigS2C chunk : assetChunks) {
            sender.accept(ServerConfigurationNetworking.createClientboundPacket(chunk));
        }
    }

    @Override
    public Type type() {
        return TYPE;
    }
}
