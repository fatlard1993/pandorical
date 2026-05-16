package justfatlard.pandorical.config;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.protocol.SyncAssetsConfigS2C;
import justfatlard.pandorical.protocol.SyncContentConfigS2C;
import justfatlard.pandorical.protocol.SyncContentS2C;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ConfigurationTask;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;

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

    public PandoricalSyncTask(
            List<SyncContentS2C.BlockEntry> blocks,
            List<SyncContentS2C.ItemEntry> items,
            List<SyncAssetsConfigS2C> assetChunks,
            List<String> entityTypes,
            List<String> blockEntityTypes,
            List<String> villagerProfessions,
            List<String> poiTypes,
            List<String> menuTypes,
            List<String> recipeBookCategories) {
        this.blocks = blocks;
        this.items = items;
        this.assetChunks = assetChunks;
        this.entityTypes = entityTypes;
        this.blockEntityTypes = blockEntityTypes;
        this.villagerProfessions = villagerProfessions;
        this.poiTypes = poiTypes;
        this.menuTypes = menuTypes;
        this.recipeBookCategories = recipeBookCategories;
    }

    @Override
    public void start(Consumer<Packet<?>> sender) {
        // Send content definitions
        int expectedChunks = assetChunks.size();
        var contentPacket = new SyncContentConfigS2C(blocks, items, expectedChunks,
            entityTypes, blockEntityTypes, villagerProfessions, poiTypes, menuTypes, recipeBookCategories);
        sender.accept(ServerConfigurationNetworking.createClientboundPacket(contentPacket));

        Pandorical.LOGGER.info("Sending config-phase content sync: {} blocks, {} items, {} entity types, " +
            "{} block entity types, {} villager professions, {} POI types, {} menu types, " +
            "{} recipe book categories, {} asset chunks",
            blocks.size(), items.size(), entityTypes.size(), blockEntityTypes.size(),
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
