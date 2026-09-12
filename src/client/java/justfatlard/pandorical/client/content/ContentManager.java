package justfatlard.pandorical.client.content;

import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.api.EntityRendererRegistry;
import justfatlard.pandorical.client.renderer.ClientEntityRendererRegistry;
import justfatlard.pandorical.protocol.*;
import justfatlard.pandorical.rail.RailCollision;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackMetadataResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * Client-side content sync: registers the server's blocks, items and registry stubs, fills the
 * {@link VirtualResourcePack}, reloads resources and acknowledges the server. Two paths:
 *
 * <p><b>Configuration phase</b>, driven by the server's PandoricalSyncTask, on the network thread:
 * {@link #handleConfigSyncContent} and {@link #handleConfigSyncAssets} collect chunks, then
 * {@link #forceFinalizeConfig} unpacks, registers with {@link StateIds#AT_JOIN}, reloads on the
 * render thread if the pack holds anything, and acks once that is done ({@link #ackConfigReady}).
 * At JOIN, PandoricalClient calls {@link #remapBlockStateIds}.
 *
 * <p><b>Play phase</b>, the fallback when the configuration phase did not make the client
 * content-ready, on the client thread: {@link #handleSyncContent} and {@link #handleSyncAssets}
 * collect, then {@link #forceFinalize} registers with {@link StateIds#AT_REGISTRATION} unless the
 * configuration phase already did, injects the pack and acks without waiting for the reload.
 * {@link #tick} finalizes with what arrived after {@link #SYNC_TIMEOUT_MS}.
 *
 * <p>Both register through {@link #registerContent} between unfreezing and re-freezing
 * {@link #SYNCED_REGISTRIES}. {@link #reset} clears both.
 */
public class ContentManager {
    private static final VirtualResourcePack virtualPack = new VirtualResourcePack();
    private static volatile SyncContentS2C pendingContent = null;
    private static final List<byte[]> assetChunks = new ArrayList<>();
    private static volatile int expectedAssetChunks = -1; // -1 = not yet known
    private static volatile boolean contentRegistered = false;
    private static long syncStartTime = 0;

    // Lookups on an unfrozen registry may miss entries registered during this sync.
    private static final Map<Identifier, Block> registeredBlocks = new HashMap<>();

    private static final int MAX_ASSET_CHUNKS = 8192;

    private static final int MAX_CONTENT_CHUNKS = 512;
    private static final int MAX_ASSET_BYTES = 50 * 1024 * 1024;
    private static final int MAX_SINGLE_ASSET = 10 * 1024 * 1024;
    private static final long SYNC_TIMEOUT_MS = 30_000;

    public static void reset() {
        pendingContent = null;
        RailCollision.setSolid(false);
        assetChunks.clear();
        expectedAssetChunks = -1;
        contentRegistered = false;
        syncStartTime = 0;
        syncing = false;
        configPhaseSynced = false;
        configReloadDone = false;
        registeredBlocks.clear();
        climbable.clear();
        interactive.clear();
        pendingConfigContent = null;
        registeredContent = null;
        configAssetChunks.clear();
        expectedConfigAssetChunks = -1;
        configContentChunks.clear();
        expectedConfigContentChunks = -1;

        // The last server's assets stay in the atlases and Language table until the next join
        // reloads. Do not reload here: a reload on disconnect hangs the loading screen.
        virtualPack.clear();
    }

    private static volatile boolean syncing = false;
    private static volatile boolean configPhaseSynced = false;

    private static volatile SyncContentConfigS2C pendingConfigContent = null;
    private static volatile SyncedContent registeredContent = null;

    /** Nothing may be registered until every chunk lands, or Fabric's registry sync fails. */
    private static final List<SyncContentConfigS2C> configContentChunks = new ArrayList<>();
    private static volatile int expectedConfigContentChunks = -1;
    private static final List<byte[]> configAssetChunks = new ArrayList<>();
    private static volatile int expectedConfigAssetChunks = -1;

    public static boolean isSyncing() { return syncing && !contentRegistered; }


    public static String getSyncStatus() {
        if (!syncing) return "";
        if (pendingContent == null) return "Connecting to server...";
        if (expectedAssetChunks <= 0) return "Registering content...";
        int received = 0;
        for (byte[] chunk : assetChunks) {
            if (chunk != null) received++;
        }
        if (received < expectedAssetChunks) {
            return String.format("Syncing assets... %d/%d", received, expectedAssetChunks);
        }
        return "Registering content...";
    }

    public static void handleSyncContent(SyncContentS2C payload) {
        pendingContent = payload;
        expectedAssetChunks = payload.expectedAssetChunks();
        syncStartTime = System.currentTimeMillis();
        syncing = true;
        Pandorical.LOGGER.info("Received content sync: {} blocks, {} items, expecting {} asset chunks",
            payload.blocks().size(), payload.items().size(), expectedAssetChunks);

        tryFinalize();
    }

    public static void handleSyncAssets(SyncAssetsS2C payload) {
        if (contentRegistered) {
            Pandorical.LOGGER.warn("Received asset chunk after content already registered — ignoring");
            return;
        }

        expectedAssetChunks = payload.totalChunks();
        if (syncStartTime == 0) syncStartTime = System.currentTimeMillis();

        // totalChunks is the sender's claim too, so both are bounded by a constant before the
        // fill loop allocates.
        if (payload.totalChunks() < 0 || payload.totalChunks() > MAX_ASSET_CHUNKS
                || payload.chunkIndex() < 0 || payload.chunkIndex() >= payload.totalChunks()) {
            Pandorical.LOGGER.warn("Rejecting asset chunk {}/{} (max {})",
                payload.chunkIndex(), payload.totalChunks(), MAX_ASSET_CHUNKS);
            return;
        }

        while (assetChunks.size() <= payload.chunkIndex()) {
            assetChunks.add(null);
        }
        assetChunks.set(payload.chunkIndex(), payload.data());

        Pandorical.LOGGER.debug("Received asset chunk {}/{}", payload.chunkIndex() + 1, payload.totalChunks());

        tryFinalize();
    }

    public static void tick() {
        if (syncStartTime > 0 && !contentRegistered) {
            if (System.currentTimeMillis() - syncStartTime > SYNC_TIMEOUT_MS) {
                Pandorical.LOGGER.warn("Content sync timed out after {}ms — finalizing with available data",
                    SYNC_TIMEOUT_MS);
                if (expectedAssetChunks > 0) {
                    long received = assetChunks.stream().filter(Objects::nonNull).count();
                    Pandorical.LOGGER.warn("Received {}/{} asset chunks before timeout", received, expectedAssetChunks);
                }
                forceFinalize();
            }
        }
    }

    /**
     * The client predicts the whole dig from these and the server does not correct it until the
     * block breaks. A negative value keeps the base block's.
     */
    private static void applyMiningProperties(BlockBehaviour.Properties props,
            SyncContentS2C.BlockEntry entry) {
        if (entry.destroyTime() >= 0.0F) {
            props.destroyTime(entry.destroyTime());
        }
        // The field, because the builder method can only turn this on.
        if (entry.requiresCorrectTool() >= 0) {
            props.requiresCorrectToolForDrops = entry.requiresCorrectTool() == 1;
        }
    }

    public static void handleConfigSyncContent(SyncContentConfigS2C payload) {
        if (configPhaseSynced) {
            Pandorical.LOGGER.warn("Config phase: received content chunk after already synced — ignoring");
            return;
        }

        int total = payload.totalChunks();
        if (total <= 0 || total > MAX_CONTENT_CHUNKS
                || payload.chunkIndex() < 0 || payload.chunkIndex() >= total) {
            Pandorical.LOGGER.warn("Rejecting config content chunk {}/{} (max {})",
                payload.chunkIndex(), total, MAX_CONTENT_CHUNKS);
            return;
        }

        expectedConfigContentChunks = total;
        expectedConfigAssetChunks = payload.expectedAssetChunks();
        syncStartTime = System.currentTimeMillis();
        syncing = true;

        while (configContentChunks.size() <= payload.chunkIndex()) {
            configContentChunks.add(null);
        }
        configContentChunks.set(payload.chunkIndex(), payload);

        Pandorical.LOGGER.info("Config phase: content chunk {}/{} ({} blocks, {} items), expecting {} asset chunks",
            payload.chunkIndex() + 1, total, payload.blocks().size(), payload.items().size(),
            expectedConfigAssetChunks);

        if (allConfigContentReceived()) {
            pendingConfigContent = joinConfigContent();
        }

        tryFinalizeConfig();
    }

    private static boolean allConfigContentReceived() {
        if (expectedConfigContentChunks <= 0) return false;
        return configContentChunks.size() == expectedConfigContentChunks
            && configContentChunks.stream().noneMatch(Objects::isNull);
    }

    /** Only blocks and items are split across chunks; every chunk carries the same stub lists. */
    private static SyncContentConfigS2C joinConfigContent() {
        var blocks = new ArrayList<SyncContentS2C.BlockEntry>();
        var items = new ArrayList<SyncContentS2C.ItemEntry>();
        for (SyncContentConfigS2C chunk : configContentChunks) {
            blocks.addAll(chunk.blocks());
            items.addAll(chunk.items());
        }

        SyncContentConfigS2C first = configContentChunks.get(0);
        Pandorical.LOGGER.info("Config phase: {} content chunk(s) assembled — {} blocks, {} items",
            configContentChunks.size(), blocks.size(), items.size());

        return new SyncContentConfigS2C(blocks, items, 0, 1, first.expectedAssetChunks(),
            first.entityTypes(), first.blockEntityTypes(), first.villagerProfessions(),
            first.poiTypes(), first.menuTypes(), first.recipeBookCategories(), first.solidRails());
    }

    public static void handleConfigSyncAssets(SyncAssetsConfigS2C payload) {
        if (configPhaseSynced) {
            Pandorical.LOGGER.warn("Config phase: received asset chunk after already synced — ignoring");
            return;
        }

        expectedConfigAssetChunks = payload.totalChunks();

        if (payload.totalChunks() < 0 || payload.totalChunks() > MAX_ASSET_CHUNKS
                || payload.chunkIndex() < 0 || payload.chunkIndex() >= payload.totalChunks()) {
            Pandorical.LOGGER.warn("Rejecting config asset chunk {}/{} (max {})",
                payload.chunkIndex(), payload.totalChunks(), MAX_ASSET_CHUNKS);
            return;
        }

        while (configAssetChunks.size() <= payload.chunkIndex()) {
            configAssetChunks.add(null);
        }
        configAssetChunks.set(payload.chunkIndex(), payload.data());

        Pandorical.LOGGER.debug("Config phase: received asset chunk {}/{}", payload.chunkIndex() + 1, payload.totalChunks());

        tryFinalizeConfig();
    }

    private static boolean allConfigAssetsReceived() {
        if (expectedConfigAssetChunks == 0) return true;
        if (expectedConfigAssetChunks < 0) return false;
        return configAssetChunks.size() == expectedConfigAssetChunks
            && configAssetChunks.stream().noneMatch(Objects::isNull);
    }

    private static void tryFinalizeConfig() {
        if (pendingConfigContent == null) return;
        if (!allConfigContentReceived()) return;
        if (configPhaseSynced) return;
        if (!allConfigAssetsReceived()) return;
        forceFinalizeConfig();
    }

    private static synchronized void forceFinalizeConfig() {
        if (configPhaseSynced) return;
        configPhaseSynced = true;
        contentRegistered = true;
        syncStartTime = 0;

        SyncContentConfigS2C content = pendingConfigContent;
        if (content == null) {
            ClientConfigurationNetworking.send(new ContentReadyConfigC2S());
            return;
        }

        if (expectedConfigAssetChunks > 0) {
            unpackAssets(configAssetChunks, true);
        }

        // No reconnect fast path: each register method is idempotent per entry, and a mod
        // installed on both sides pre-registers its own entries, so "some present" does not
        // mean "all present".
        {
            for (Registry<?> registry : SYNCED_REGISTRIES) unfreezeRegistry(registry);

            try {
                int stubCount = registerContent(content, StateIds.AT_JOIN);

                Pandorical.LOGGER.info("Config phase: registered {} blocks, {} items, and {} additional registry stubs",
                    content.blocks().size(), content.items().size(), stubCount);
            } finally {
                try {
                    for (Registry<?> registry : SYNCED_REGISTRIES) freezeRegistry(registry);
                } catch (Exception e) {
                    Pandorical.LOGGER.warn("Failed to re-freeze registries: {}", e.getMessage());
                }
            }
        }

        // Reload before a level exists, as vanilla does for a server resource pack: a reload
        // during level creation crashes some Windows OpenGL drivers (MC-311345). The ack waits
        // for it, so the server holds the login until then.
        if (virtualPack.hasResources()) {
            Minecraft.getInstance().execute(() -> injectResourcePack(ContentManager::ackConfigReady));
        } else {
            ackConfigReady();
        }
    }

    private static volatile boolean configReloadDone = false;

    public static boolean wasConfigReloadDone() {
        return configReloadDone;
    }

    private static void ackConfigReady() {
        configReloadDone = true;
        try {
            ClientConfigurationNetworking.send(new ContentReadyConfigC2S());
            Pandorical.LOGGER.info("Config phase: sent ContentReadyConfigC2S acknowledgment");
        } catch (Exception e) {
            Pandorical.LOGGER.warn("Config phase: could not acknowledge content sync: {}", e.toString());
        }
    }

    private static final Set<Block> climbable =
        Collections.newSetFromMap(new ConcurrentHashMap<>());

    /** A set, not a constructor flag: a reconnect reuses the previous session's block objects. */
    private static final Set<Block> interactive =
        Collections.newSetFromMap(new IdentityHashMap<>());

    public static boolean isInteractive(BlockState state) {
        return !interactive.isEmpty() && interactive.contains(state.getBlock());
    }

    public static boolean isClimbable(BlockState state) {
        return !climbable.isEmpty() && climbable.contains(state.getBlock());
    }

    private static void unpackAssets(List<byte[]> chunks, boolean configPhase) {
        try {
            long totalSize = chunks.stream().filter(Objects::nonNull).mapToLong(c -> c.length).sum();
            if (totalSize > MAX_ASSET_BYTES) {
                Pandorical.LOGGER.error(configPhase
                    ? "Config phase: asset data too large: {} bytes (max {})"
                    : "Asset data too large: {} bytes (max {})", totalSize, MAX_ASSET_BYTES);
                return;
            }

            ByteArrayOutputStream assembled = new ByteArrayOutputStream();
            for (byte[] chunk : chunks) {
                if (chunk != null) assembled.write(chunk);
            }

            byte[] compressed = assembled.toByteArray();
            ByteArrayOutputStream decompressedBaos = new ByteArrayOutputStream();
            try (GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
                byte[] buf = new byte[8192];
                int read;
                long total = 0;
                while ((read = gzis.read(buf)) != -1) {
                    total += read;
                    if (total > MAX_ASSET_BYTES) {
                        Pandorical.LOGGER.error(configPhase
                            ? "Config phase: decompressed data exceeds {}MB limit"
                            : "Decompressed asset data exceeds {}MB limit — aborting",
                            MAX_ASSET_BYTES / 1024 / 1024);
                        return;
                    }
                    decompressedBaos.write(buf, 0, read);
                }
            }

            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(decompressedBaos.toByteArray()));
            int count = 0;
            while (dis.available() > 0) {
                String path = dis.readUTF();
                int len = dis.readInt();
                if (len < 0 || len > MAX_SINGLE_ASSET) {
                    Pandorical.LOGGER.error(configPhase
                        ? "Config phase: asset '{}' has invalid size: {} bytes"
                        : "Asset '{}' has invalid size: {} bytes", path, len);
                    break;
                }
                byte[] data = new byte[len];
                dis.readFully(data);
                virtualPack.addResource(path, data);
                count++;
            }

            Pandorical.LOGGER.info(configPhase
                ? "Config phase: unpacked {} assets"
                : "Unpacked {} assets from server", count);
        } catch (IOException e) {
            Pandorical.LOGGER.error(configPhase
                ? "Config phase: failed to unpack assets: {}"
                : "Failed to unpack assets: {}", e.getMessage(), e);
        }
    }

    public static boolean wasConfigPhaseSynced() {
        return configPhaseSynced;
    }

    private static boolean allAssetsReceived() {
        if (expectedAssetChunks == 0) return true;
        if (expectedAssetChunks < 0) return false;
        return assetChunks.size() == expectedAssetChunks
            && assetChunks.stream().noneMatch(Objects::isNull);
    }

    private static void tryFinalize() {
        if (pendingContent == null) return;
        if (contentRegistered) return;
        if (!allAssetsReceived()) return;
        forceFinalize();
    }

    private static synchronized void forceFinalize() {
        if (contentRegistered) return;
        contentRegistered = true;
        syncStartTime = 0;

        SyncContentS2C content = pendingContent;
        if (content == null) {
            ClientPlayNetworking.send(new ContentReadyC2S());
            return;
        }

        if (!configPhaseSynced) {
            for (Registry<?> registry : SYNCED_REGISTRIES) unfreezeRegistry(registry);

            try {
                int stubCount = registerContent(content, StateIds.AT_REGISTRATION);

                sweepUnmappedStateIds(content.blocks());

                Pandorical.LOGGER.info("Play phase fallback: registered {} blocks, {} items, and {} stubs on client",
                    content.blocks().size(), content.items().size(), stubCount);
            } finally {
                for (Registry<?> registry : SYNCED_REGISTRIES) freezeRegistry(registry);
            }
        } else {
            Pandorical.LOGGER.debug("Play phase: skipping block/item/stub registration — already done in config phase");
        }

        if (assetChunks.stream().anyMatch(Objects::nonNull)) unpackAssets(assetChunks, false);
        injectResourcePack();

        ClientPlayNetworking.send(new ContentReadyC2S());
    }

    /** The caller unfreezes {@link #SYNCED_REGISTRIES}. Returns how many stubs were registered. */
    private static int registerContent(SyncedContent content, StateIds stateIds) {
        registeredContent = content;
        RailCollision.setSolid(content.solidRails());
        for (SyncContentS2C.BlockEntry entry : content.blocks()) {
            registerBlock(entry, stateIds);
        }
        for (SyncContentS2C.ItemEntry entry : content.items()) {
            registerItem(entry);
        }

        int stubCount = 0;
        stubCount += registerEntityTypeStubs(content.entityTypes());
        stubCount += registerBlockEntityTypeStubs(content.blockEntityTypes());
        stubCount += registerVillagerProfessionStubs(content.villagerProfessions());
        stubCount += registerPoiTypeStubs(content.poiTypes());
        stubCount += registerMenuTypeStubs(content.menuTypes());
        stubCount += registerRecipeBookCategoryStubs(content.recipeBookCategories());
        return stubCount;
    }

    private static boolean packSourceInjected = false;

    public static void injectResourcePack() {
        injectResourcePack(() -> {});
    }

    /**
     * {@code afterReload} runs exactly once on every path, a throw included: after the reload,
     * or at once when there is nothing to reload. A caller waiting to ack depends on it.
     */
    public static void injectResourcePack(Runnable afterReload) {
        AtomicBoolean ran = new AtomicBoolean();
        Runnable once = () -> {
            if (ran.compareAndSet(false, true)) afterReload.run();
        };
        try {
            injectAndReload(once);
        } catch (RuntimeException e) {
            Pandorical.LOGGER.error("Could not put the synced pack in place", e);
            once.run();
        }
    }

    private static void injectAndReload(Runnable afterReload) {
        if (!virtualPack.hasResources()) {
            Pandorical.LOGGER.warn("Virtual pack has no resources — skipping injection");
            afterReload.run();
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            Pandorical.LOGGER.warn("Minecraft client is null — cannot inject resource pack");
            afterReload.run();
            return;
        }

        var namespaces = virtualPack.getNamespaces(PackType.CLIENT_RESOURCES);
        Pandorical.LOGGER.info("Virtual pack contains {} namespaces: {}", namespaces.size(), namespaces);

        // A RepositorySource keeps the pack in every later reload. Once per game: the source
        // reads the static pack.
        try {
            var packRepo = client.getResourcePackRepository();
            if (packSourceInjected) {
                Pandorical.LOGGER.debug("Pandorical virtual pack source already registered");
            } else {
            var sourcesField = PackRepository.class.getDeclaredField("sources");
            sourcesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            var sources = (Set<RepositorySource>) sourcesField.get(packRepo);

            var pandoricalSource = new RepositorySource() {
                @Override
                public void loadPacks(Consumer<Pack> consumer) {
                    Pandorical.LOGGER.debug("PackRepository is loading packs — providing Pandorical virtual pack");
                    var supplier = new Pack.ResourcesSupplier() {
                        @Override
                        public PackMetadataResources openMetadata(PackLocationInfo info) { return virtualPack; }
                        @Override
                        public Stream<PackResources> openResources(PackLocationInfo info, Pack.Metadata metadata) { return Stream.of(virtualPack); }
                    };

                    var metadata = new Pack.Metadata(
                        Component.literal("Pandorical synced assets"),
                        PackCompatibility.COMPATIBLE,
                        FeatureFlags.DEFAULT_FLAGS,
                        List.of()
                    );

                    var pack = new Pack(
                        virtualPack.location(),
                        supplier,
                        metadata,
                        new PackSelectionConfig(true, Pack.Position.TOP, false)
                    );
                    consumer.accept(pack);
                }
            };

            var mutableSources = new LinkedHashSet<>(sources);
            mutableSources.add(pandoricalSource);
            sourcesField.set(packRepo, mutableSources);
            packSourceInjected = true;

            Pandorical.LOGGER.info("Added Pandorical virtual pack source to PackRepository ({} sources total) — triggering reload",
                mutableSources.size());
            }
        } catch (Exception e) {
            Pandorical.LOGGER.error("Failed to add virtual pack source: {}", e.getMessage(), e);
            afterReload.run();
            return;
        }

        // Both before the reload: tints so tinted models render right, and tab items because
        // tab contents are rebuilt during it.
        registerBlockColors(client);
        registerCreativeTabItems();

        CompletableFuture<Void> reload = client.reloadResourcePacks();
        reload.whenComplete((unused, error) -> {
            if (error != null) Pandorical.LOGGER.error("Resource reload failed: {}", error.toString());
            afterReload.run();
        });
        reload.thenRun(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.levelRenderer != null && mc.level != null) {
                mc.levelRenderer.invalidateCompiledGeometry(
                    mc.level,
                    mc.options,
                    mc.gameRenderer.mainCamera(),
                    mc.getBlockColors()
                );
                Pandorical.LOGGER.info("Resource reload complete — invalidated compiled geometry for full re-render");
            }

            var repo = mc.getResourcePackRepository();
            boolean found = false;
            for (var pack : repo.getSelectedPacks()) {
                if ("pandorical_virtual".equals(pack.getId())) {
                    found = true;
                    break;
                }
            }
            if (found) {
                Pandorical.LOGGER.info("Pandorical virtual pack is in selected packs — models should load");
            } else {
                Pandorical.LOGGER.error("Pandorical virtual pack NOT found in selected packs! Available: {}, Selected: {}",
                    repo.getAvailableIds(), repo.getSelectedIds());
            }

        });
    }

    private static void registerCreativeTabItems() {
        SyncedContent content = registeredContent;
        if (content == null) return;
        try {
            List<Item> buildingBlocks = new ArrayList<>();
            List<Item> combat = new ArrayList<>();
            List<Item> tools = new ArrayList<>();
            List<Item> ingredients = new ArrayList<>();
            List<Item> naturalBlocks = new ArrayList<>();
            List<Item> functional = new ArrayList<>();

            for (SyncContentS2C.ItemEntry entry : content.items()) {
                Identifier id = Identifier.tryParse(entry.id());
                if (id == null) continue;
                Item item = BuiltInRegistries.ITEM.getValue(id);
                if (item == null || item == Items.AIR) continue;

                if (!entry.equipSlot().isEmpty()) {
                    combat.add(item);
                } else if (!entry.toolType().isEmpty()) {
                    tools.add(item);
                } else if (item instanceof BlockItem blockItem) {
                    var block = blockItem.getBlock();
                    String blockId = entry.id();
                    if (blockId.contains("slab") || blockId.contains("stair") || blockId.contains("fence")
                            || blockId.contains("wall") || blockId.contains("post") || blockId.contains("floor")) {
                        buildingBlocks.add(item);
                    } else if (blockId.contains("crop") || blockId.contains("flower") || blockId.contains("bush")
                            || blockId.contains("sapling") || blockId.contains("grass") || blockId.contains("moss")
                            || blockId.contains("vine") || blockId.contains("mushroom") || blockId.contains("fern")
                            || blockId.contains("leaf") || blockId.contains("petals") || blockId.contains("snow")) {
                        naturalBlocks.add(item);
                    } else {
                        functional.add(item);
                    }
                } else {
                    ingredients.add(item);
                }
            }

            registerForTab(CreativeModeTabs.BUILDING_BLOCKS, buildingBlocks);
            registerForTab(CreativeModeTabs.COMBAT, combat);
            registerForTab(CreativeModeTabs.TOOLS_AND_UTILITIES, tools);
            registerForTab(CreativeModeTabs.NATURAL_BLOCKS, naturalBlocks);
            registerForTab(CreativeModeTabs.FUNCTIONAL_BLOCKS, functional);
            registerForTab(CreativeModeTabs.INGREDIENTS, ingredients);

            int total = buildingBlocks.size() + combat.size() + tools.size()
                + naturalBlocks.size() + functional.size() + ingredients.size();
            Pandorical.LOGGER.info("Registered {} items for creative tabs (building={}, combat={}, tools={}, natural={}, functional={}, ingredients={})",
                total, buildingBlocks.size(), combat.size(), tools.size(),
                naturalBlocks.size(), functional.size(), ingredients.size());
        } catch (Exception e) {
            Pandorical.LOGGER.warn("Failed to register creative tab items: {}", e.getMessage());
        }
    }

    private static final Map<ResourceKey<CreativeModeTab>, List<Item>> tabItems =
        new ConcurrentHashMap<>();

    private static void registerForTab(ResourceKey<CreativeModeTab> tabKey,
                                        List<Item> items) {
        boolean listening = tabItems.containsKey(tabKey);
        tabItems.put(tabKey, List.copyOf(items));
        if (listening) return;
        CreativeModeTabEvents
            .modifyOutputEvent(tabKey)
            .register(output -> {
                for (Item item : tabItems.getOrDefault(tabKey, List.of())) {
                    output.accept(item);
                }
            });
    }


    private static void registerBlockColors(Minecraft client) {
        SyncedContent content = registeredContent;
        if (content == null) return;

        var blockColors = client.getBlockColors();
        int registered = 0;

        for (SyncContentS2C.BlockEntry entry : content.blocks()) {
            try {
                Identifier id = Identifier.tryParse(entry.id());
                if (id == null) continue;

                Block block = BuiltInRegistries.BLOCK.getValue(id);
                if (block == null) continue;

                List<BlockTintSource> tintSources = null;

                String baseBlockId = entry.baseBlockId();
                if (baseBlockId != null && !baseBlockId.isEmpty()) {
                    Identifier baseId = Identifier.tryParse(baseBlockId);
                    if (baseId != null) {
                        Block baseBlock = BuiltInRegistries.BLOCK.getValue(baseId);
                        if (baseBlock != null) {
                            var inherited = blockColors.getTintSources(baseBlock.defaultBlockState());
                            if (inherited != null && !inherited.isEmpty()) {
                                tintSources = inherited;
                            }
                        }
                    }
                }

                // No default tint: break particles take the block colour even where the model has
                // no tintindex. A block wanting one names a tinted base or uses BlockTintApi.
                if (tintSources == null) continue;

                blockColors.register(tintSources, block);
                registered++;
            } catch (Exception e) {
                Pandorical.LOGGER.warn("Failed to register color provider for {}: {}", entry.id(), e.getMessage());
            }
        }

        Pandorical.LOGGER.info("Registered block color providers for {} blocks", registered);
    }

    /** Also picks which path's wording {@link #registerBlock} logs with. */
    private enum StateIds {
        /** Unassigned until {@link #remapBlockStateIds()} runs at JOIN, before chunks decode. */
        AT_JOIN,
        AT_REGISTRATION
    }

    private static void registerBlock(SyncContentS2C.BlockEntry entry, StateIds stateIds) {
        boolean configPhase = stateIds == StateIds.AT_JOIN;
        try {
            Identifier id = Identifier.tryParse(entry.id());
            if (id == null) {
                Pandorical.LOGGER.warn(configPhase
                    ? "Config phase: invalid block ID: '{}'"
                    : "Invalid block ID: '{}'", entry.id());
                return;
            }

            if (BuiltInRegistries.BLOCK.containsKey(id)) {
                Block existing = BuiltInRegistries.BLOCK.getValue(id);
                registeredBlocks.put(id, existing);
                DynamicBlock.applyShapeData(existing, entry.shapeData());
                if (entry.climbable()) climbable.add(existing);
                if (entry.interactive()) interactive.add(existing);
                if (configPhase) {
                    Pandorical.LOGGER.debug("Config phase: block {} already registered — reusing", entry.id());
                }
                return;
            }

            Identifier baseId = Identifier.tryParse(entry.baseBlockId());
            BlockBehaviour.Properties props;
            if (baseId != null) {
                Block baseBlock = BuiltInRegistries.BLOCK.getValue(baseId);
                if (baseBlock != null) {
                    props = BlockBehaviour.Properties.ofFullCopy(baseBlock);
                } else {
                    Pandorical.LOGGER.warn(configPhase
                        ? "Config phase: base block '{}' not found for '{}' — using defaults"
                        : "Base block '{}' not found for '{}' — using defaults",
                        entry.baseBlockId(), entry.id());
                    props = BlockBehaviour.Properties.of();
                }
            } else {
                props = BlockBehaviour.Properties.of();
            }

            if (!DynamicBlock.declaresCollision(entry.shapeData())) props.noCollision();

            applyMiningProperties(props, entry);

            ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
            props.setId(key);

            Block baseBlock = baseId != null ? BuiltInRegistries.BLOCK.getValue(baseId) : null;
            List<Property<?>> stateProps = new ArrayList<>();
            for (String propSpec : entry.stateProperties()) {
                StatePropertySpec spec = StatePropertySpec.parse(propSpec, entry.id());
                var prop = DynamicBlock.resolveProperty(spec.name(), baseBlock, spec.valueCount(), spec.intMin(),
                    spec.type(), spec.enumValues());
                if (prop != null) {
                    stateProps.add(prop);
                } else {
                    Pandorical.LOGGER.warn(configPhase
                        ? "Config phase: unknown state property '{}' (type={}, values={}) for block '{}'"
                        : "Unknown state property '{}' (type={}, values={}) for block '{}'",
                        spec.name(), spec.type(), spec.valueCount(), entry.id());
                }
            }

            // Light is fixed into each state as the block is built.
            byte[] light = entry.lightData();
            if (light != null && light.length > 0) {
                props.lightLevel(state -> DynamicBlock.lightFor(state, light));
            }
            Block block = createBlock(props, stateProps, baseBlock, entry.stateProperties());
            Registry.register(BuiltInRegistries.BLOCK, id, block);
            registeredBlocks.put(id, block);
            if (entry.climbable()) climbable.add(block);
            if (entry.interactive()) interactive.add(block);

            DynamicBlock.applyShapeData(block, entry.shapeData());

            if (stateIds == StateIds.AT_JOIN) {
                Pandorical.LOGGER.debug("Config phase: registered block {} (base: {}, class: {}, states: {})",
                    entry.id(), entry.baseBlockId(), block.getClass().getSimpleName(),
                    block.getStateDefinition().getPossibleStates().size());
            } else {
                var possibleStates = block.getStateDefinition().getPossibleStates();
                if (entry.stateIds().size() == possibleStates.size()) {
                    for (int i = 0; i < possibleStates.size(); i++) {
                        Block.BLOCK_STATE_REGISTRY.addMapping(possibleStates.get(i), entry.stateIds().get(i));
                    }
                } else {
                    coverStateIdsWithFallback(entry, block.defaultBlockState(),
                        "state count mismatch (server=" + entry.stateIds().size()
                            + ", client=" + possibleStates.size()
                            + ", client properties=" + propertyNames(block)
                            + ", server properties=" + entry.stateProperties() + ")");
                }

                Pandorical.LOGGER.debug("Registered client block: {} (base: {}, states: {}, ids: {})",
                    entry.id(), entry.baseBlockId(), possibleStates.size(), entry.stateIds());
            }
        } catch (Exception e) {
            Pandorical.LOGGER.error(configPhase
                ? "Config phase: failed to register block {}: {}"
                : "Failed to register block {}: {}", entry.id(), e.getMessage(), e);
        }
    }

    /**
     * {@code spec} is "slot" or "slot|asset". Without an equipment asset the renderer lays the
     * item's flat sprite on the wearer.
     */
    private static void applyEquippable(Item.Properties props, String spec) {
        if (spec.isEmpty()) return;

        String[] parts = spec.split("\\|", 2);
        var slot = EquipmentSlot.byName(parts[0]);
        if (slot == null) {
            Pandorical.LOGGER.warn("Unknown equipment slot '{}' — item left unwearable", parts[0]);
            return;
        }

        if (parts.length < 2 || parts[1].isEmpty()) {
            props.equippable(slot);
            return;
        }

        Identifier asset = Identifier.tryParse(parts[1]);
        if (asset == null) {
            props.equippable(slot);
            return;
        }

        props.component(DataComponents.EQUIPPABLE,
            Equippable.builder(slot)
                .setAsset(ResourceKey.create(
                    EquipmentAssets.ROOT_ID, asset))
                .build());
    }

    private static void applyFood(Item.Properties props, String spec) {
        if (spec.isEmpty()) return;

        String[] p = spec.split("\\|");
        if (p.length != 4) {
            Pandorical.LOGGER.warn("Unreadable food spec '{}' — leaving the item inedible", spec);
            return;
        }

        try {
            var food = new FoodProperties.Builder()
                .nutrition(Integer.parseInt(p[0]))
                .saturationModifier(Float.parseFloat(p[1]));
            if (Boolean.parseBoolean(p[2])) food.alwaysEdible();

            props.food(food.build(), Consumable.builder()
                .consumeSeconds(Float.parseFloat(p[3]))
                .build());
        } catch (RuntimeException e) {
            Pandorical.LOGGER.warn("Unreadable food spec '{}' — leaving the item inedible", spec, e);
        }
    }

    /** A spec without '|', such as an older server's plain "tool", leaves the item plain. */
    private static void applyTool(Item.Properties props, String spec) {
        if (spec.isEmpty() || !spec.contains("|")) return;

        String[] p = spec.split("\\|");
        if (p.length != 9) {
            Pandorical.LOGGER.warn("Unreadable tool spec '{}' — leaving the item plain", spec);
            return;
        }

        try {
            var material = new ToolMaterial(
                TagKey.create(Registries.BLOCK, Identifier.parse(p[1])),
                Integer.parseInt(p[2]),
                Float.parseFloat(p[3]),
                Float.parseFloat(p[4]),
                Integer.parseInt(p[5]),
                TagKey.create(Registries.ITEM, Identifier.parse(p[6])));
            float damage = Float.parseFloat(p[7]);
            float speed = Float.parseFloat(p[8]);

            switch (p[0]) {
                case "axe"     -> props.axe(material, damage, speed);
                case "pickaxe" -> props.pickaxe(material, damage, speed);
                case "shovel"  -> props.shovel(material, damage, speed);
                case "hoe"     -> props.hoe(material, damage, speed);
                case "sword"   -> props.sword(material, damage, speed);
                default -> Pandorical.LOGGER.warn("Unknown tool kind '{}' — leaving the item plain", p[0]);
            }
        } catch (RuntimeException e) {
            Pandorical.LOGGER.warn("Could not rebuild tool from '{}': {}", spec, e.toString());
        }
    }

    private static void registerItem(SyncContentS2C.ItemEntry entry) {
        try {
            Identifier id = Identifier.tryParse(entry.id());
            if (id == null) {
                Pandorical.LOGGER.warn("Invalid item ID: '{}'", entry.id());
                return;
            }

            if (BuiltInRegistries.ITEM.containsKey(id)) {
                Pandorical.LOGGER.debug("Item '{}' already registered — skipping", entry.id());
                return;
            }

            ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
            var props = new Item.Properties().setId(key);

            // Durability and stack size are mutually exclusive in MC
            if (entry.maxDamage() > 0) {
                props.durability(entry.maxDamage());
            } else {
                props.stacksTo(entry.maxStackSize());
            }

            applyEquippable(props, entry.equipSlot());

            applyTool(props, entry.toolType());

            applyFood(props, entry.foodSpec());

            Item item;
            Block block = registeredBlocks.get(id);
            if (block == null) {
                block = BuiltInRegistries.BLOCK.getValue(id);
                if (block == Blocks.AIR) block = null;
            }
            if (block != null) {
                item = new BlockItem(block, props.useBlockDescriptionPrefix());
            } else {
                item = new Item(props);
            }
            registerWithHolder(BuiltInRegistries.ITEM, id, item);

        } catch (Exception e) {
            Pandorical.LOGGER.error("Failed to register item {}: {}", entry.id(), e.getMessage(), e);
        }
    }

    private static Block createBlock(BlockBehaviour.Properties props,
                                     List<Property<?>> stateProps,
                                     Block baseBlock, List<String> rawPropSpecs) {
        // The server's shapes arrive after construction; without this vanilla assumes a full
        // cube and culls neighbouring faces.
        props.noOcclusion();

        Block vanillaShaped = VanillaShapedBlocks.forBase(baseBlock, props);
        if (vanillaShaped != null) return vanillaShaped;

        boolean isSlab = baseBlock instanceof SlabBlock || isSlabFromProperties(rawPropSpecs);

        if (isSlab) {
            List<Property<?>> extraProps = new ArrayList<>();
            for (var prop : stateProps) {
                String name = prop.getName();
                if (!name.equals("type") && !name.equals("waterlogged")) {
                    extraProps.add(prop);
                }
            }

            // Even with no extra properties: DynamicBlock#applyShapeData only gives the server's
            // shapes to its own types.
            return DynamicSlabBlock.create(props, extraProps);
        }

        return DynamicBlock.create(props, stateProps);
    }

    private static boolean isSlabFromProperties(List<String> rawPropSpecs) {
        for (String spec : rawPropSpecs) {
            if (spec.startsWith("type:e:") && spec.contains("bottom") && spec.contains("top") && spec.contains("double")) {
                return true;
            }
        }
        return false;
    }

    // Inert stubs, so Fabric's registry sync accepts ids the client cannot rebuild.

    @SuppressWarnings("unchecked")
    private static int registerEntityTypeStubs(List<String> ids) {
        int count = 0;
        for (String idStr : ids) {
            try {
                Identifier id = Identifier.tryParse(idStr);
                if (id == null) {
                    Pandorical.LOGGER.warn("Config phase: invalid entity type ID: '{}'", idStr);
                    continue;
                }
                if (BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                    Pandorical.LOGGER.debug("Config phase: entity type '{}' already registered — skipping", idStr);
                    continue;
                }
                ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id);
                // Runs at spawn, after EntityRenderersS2C delivered the keys. With no key it
                // returns null and vanilla skips the spawn: a type without a renderer must not
                // reach the render dispatcher.
                final String typeIdStr = idStr;
                EntityType<?> stub = EntityType.Builder.of((type, level) -> {
                        String rendererKey = ClientEntityRendererRegistry.getRendererKey(typeIdStr);
                        Pandorical.LOGGER.debug("Stub entity factory: {} rendererKey={}", typeIdStr, rendererKey);
                        if (EntityRendererRegistry.KEY_THROWN_ITEM.equals(rendererKey)) {
                            @SuppressWarnings({"unchecked", "rawtypes"})
                            StubThrownItemEntity thrown = new StubThrownItemEntity((EntityType) type, level);
                            return thrown;
                        }
                        if (EntityRendererRegistry.KEY_INVISIBLE.equals(rendererKey)) {
                            return new StubEntity(type, level);
                        }
                        return null;
                    }, MobCategory.MISC)
                    .noSave().noSummon().sized(0.25F, 0.25F)
                    .clientTrackingRange(4).updateInterval(10)
                    .build(key);
                registerWithHolder(BuiltInRegistries.ENTITY_TYPE, id, stub);
                count++;
                Pandorical.LOGGER.debug("Config phase: registered stub entity type: {}", idStr);
            } catch (Exception e) {
                Pandorical.LOGGER.error("Config phase: failed to register stub entity type {}: {}", idStr, e.getMessage(), e);
            }
        }
        return count;
    }

    private static int registerBlockEntityTypeStubs(List<String> ids) {
        int count = 0;
        for (String idStr : ids) {
            try {
                Identifier id = Identifier.tryParse(idStr);
                if (id == null) {
                    Pandorical.LOGGER.warn("Config phase: invalid block entity type ID: '{}'", idStr);
                    continue;
                }
                if (BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(id)) {
                    Pandorical.LOGGER.debug("Config phase: block entity type '{}' already registered — skipping", idStr);
                    continue;
                }
                // Use access-widened constructor: (BlockEntitySupplier, Set<Block>)
                BlockEntityType<?> stub = new BlockEntityType<>((pos, state) -> null, Set.of());
                registerWithHolder(BuiltInRegistries.BLOCK_ENTITY_TYPE, id, stub);
                count++;
                Pandorical.LOGGER.debug("Config phase: registered stub block entity type: {}", idStr);
            } catch (Exception e) {
                Pandorical.LOGGER.error("Config phase: failed to register stub block entity type {}: {}", idStr, e.getMessage(), e);
            }
        }
        return count;
    }

    private static int registerVillagerProfessionStubs(List<String> ids) {
        int count = 0;
        for (String idStr : ids) {
            try {
                Identifier id = Identifier.tryParse(idStr);
                if (id == null) {
                    Pandorical.LOGGER.warn("Config phase: invalid villager profession ID: '{}'", idStr);
                    continue;
                }
                if (BuiltInRegistries.VILLAGER_PROFESSION.containsKey(id)) {
                    Pandorical.LOGGER.debug("Config phase: villager profession '{}' already registered — skipping", idStr);
                    continue;
                }
                VillagerProfession stub = new VillagerProfession(
                    Component.literal(idStr),
                    holder -> false,  // heldJobSite: matches nothing
                    holder -> false,  // acquirableJobSite: matches nothing
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    null,  // workSound
                    new Int2ObjectOpenHashMap<>()
                );
                registerWithHolder(BuiltInRegistries.VILLAGER_PROFESSION, id, stub);
                count++;
                Pandorical.LOGGER.debug("Config phase: registered stub villager profession: {}", idStr);
            } catch (Exception e) {
                Pandorical.LOGGER.error("Config phase: failed to register stub villager profession {}: {}", idStr, e.getMessage(), e);
            }
        }
        return count;
    }

    private static int registerPoiTypeStubs(List<String> ids) {
        int count = 0;
        for (String idStr : ids) {
            try {
                Identifier id = Identifier.tryParse(idStr);
                if (id == null) {
                    Pandorical.LOGGER.warn("Config phase: invalid POI type ID: '{}'", idStr);
                    continue;
                }
                if (BuiltInRegistries.POINT_OF_INTEREST_TYPE.containsKey(id)) {
                    Pandorical.LOGGER.debug("Config phase: POI type '{}' already registered — skipping", idStr);
                    continue;
                }
                PoiType stub = new PoiType(Set.of(), 0, 0);
                registerWithHolder(BuiltInRegistries.POINT_OF_INTEREST_TYPE, id, stub);
                count++;
                Pandorical.LOGGER.debug("Config phase: registered stub POI type: {}", idStr);
            } catch (Exception e) {
                Pandorical.LOGGER.error("Config phase: failed to register stub POI type {}: {}", idStr, e.getMessage(), e);
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private static int registerMenuTypeStubs(List<String> ids) {
        int count = 0;
        for (String idStr : ids) {
            try {
                Identifier id = Identifier.tryParse(idStr);
                if (id == null) {
                    Pandorical.LOGGER.warn("Config phase: invalid menu type ID: '{}'", idStr);
                    continue;
                }
                if (BuiltInRegistries.MENU.containsKey(id)) {
                    Pandorical.LOGGER.debug("Config phase: menu type '{}' already registered — skipping", idStr);
                    continue;
                }
                // Use access-widened constructor: (MenuSupplier, FeatureFlagSet)
                MenuType<?> stub = new MenuType<>((syncId, inv) -> null, FeatureFlags.VANILLA_SET);
                registerWithHolder(BuiltInRegistries.MENU, id, stub);
                count++;
                Pandorical.LOGGER.debug("Config phase: registered stub menu type: {}", idStr);
            } catch (Exception e) {
                Pandorical.LOGGER.error("Config phase: failed to register stub menu type {}: {}", idStr, e.getMessage(), e);
            }
        }
        return count;
    }

    private static int registerRecipeBookCategoryStubs(List<String> ids) {
        int count = 0;
        for (String idStr : ids) {
            try {
                Identifier id = Identifier.tryParse(idStr);
                if (id == null) {
                    Pandorical.LOGGER.warn("Config phase: invalid recipe book category ID: '{}'", idStr);
                    continue;
                }
                if (BuiltInRegistries.RECIPE_BOOK_CATEGORY.containsKey(id)) {
                    Pandorical.LOGGER.debug("Config phase: recipe book category '{}' already registered — skipping", idStr);
                    continue;
                }
                RecipeBookCategory stub = new RecipeBookCategory();
                registerWithHolder(BuiltInRegistries.RECIPE_BOOK_CATEGORY, id, stub);
                count++;
                Pandorical.LOGGER.debug("Config phase: registered stub recipe book category: {}", idStr);
            } catch (Exception e) {
                Pandorical.LOGGER.error("Config phase: failed to register stub recipe book category {}: {}", idStr, e.getMessage(), e);
            }
        }
        return count;
    }

    private static final List<Registry<?>> SYNCED_REGISTRIES = List.of(
        BuiltInRegistries.BLOCK,
        BuiltInRegistries.ITEM,
        BuiltInRegistries.ENTITY_TYPE,
        BuiltInRegistries.BLOCK_ENTITY_TYPE,
        BuiltInRegistries.VILLAGER_PROFESSION,
        BuiltInRegistries.POINT_OF_INTEREST_TYPE,
        BuiltInRegistries.MENU,
        BuiltInRegistries.RECIPE_BOOK_CATEGORY);

    private static void unfreezeRegistry(Registry<?> registry) {
        if (registry instanceof MappedRegistry<?> mapped) {
            mapped.frozen = false;
            // freeze() nulled this, and registerWithHolder needs it.
            try {
                var field = MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");
                field.setAccessible(true);
                if (field.get(mapped) == null) {
                    field.set(mapped, new IdentityHashMap<>());
                }
            } catch (Exception e) {
                Pandorical.LOGGER.warn("Could not restore intrusive holder cache for {}", registry, e);
            }
        }
    }

    /**
     * {@code MappedRegistry.freeze} sets {@code frozen} first, then throws "Tags already present
     * before freezing" when tags are bound, so the registry is frozen either way. Do not null
     * {@code allTags} to avoid the throw: an integrated server shares these registries, and its
     * tag sync then fails.
     */
    private static void freezeRegistry(Registry<?> registry) {
        if (registry instanceof MappedRegistry<?> mapped) {
            try {
                mapped.freeze();
            } catch (IllegalStateException e) {
                // Freeze also throws for unbound values, which is a real failure.
                String message = String.valueOf(e.getMessage());
                if (message.contains("Tags already present")) {
                    Pandorical.LOGGER.debug("Registry {} kept its existing tag bindings: {}", registry.key(), message);
                } else {
                    Pandorical.LOGGER.warn("Registry {} did not freeze cleanly: {}", registry.key(), message);
                }
            }
        }
    }

    /** Unfrozen registries need intrusive holders created manually before register(). */
    @SuppressWarnings("unchecked")
    private static <T> void registerWithHolder(Registry<T> registry, Identifier id, T entry) {
        if (registry instanceof MappedRegistry<T> mapped) {
            mapped.createIntrusiveHolder(entry);
        }
        Registry.register(registry, id, entry);
    }

    /** {@code Block.BLOCK_STATE_REGISTRY} is outside Fabric's registry sync. */
    public static void remapBlockStateIds() {
        if (pendingConfigContent == null) return;

        int remapped = 0;
        for (SyncContentS2C.BlockEntry entry : pendingConfigContent.blocks()) {
            try {
                Identifier id = Identifier.tryParse(entry.id());
                if (id == null) {
                    coverStateIdsWithFallback(entry, null, "unparseable block id");
                    continue;
                }

                Block block = BuiltInRegistries.BLOCK.getValue(id);
                if (block == null) {
                    coverStateIdsWithFallback(entry, null, "block missing from client registry");
                    continue;
                }

                var possibleStates = block.getStateDefinition().getPossibleStates();
                if (entry.stateIds().size() != possibleStates.size()) {
                    coverStateIdsWithFallback(entry, block.defaultBlockState(),
                        "state count mismatch (server=" + entry.stateIds().size()
                            + ", client=" + possibleStates.size()
                            + ", client properties=" + propertyNames(block)
                            + ", server properties=" + entry.stateProperties() + ")");
                    continue;
                }

                for (int i = 0; i < possibleStates.size(); i++) {
                    int serverId = entry.stateIds().get(i);
                    Block.BLOCK_STATE_REGISTRY.addMapping(possibleStates.get(i), serverId);
                    remapped++;
                }
            } catch (Exception e) {
                Pandorical.LOGGER.warn("Failed to remap block state IDs for {}: {}", entry.id(), e.getMessage());
                coverStateIdsWithFallback(entry, null, "remap threw: " + e.getMessage());
            }
        }

        sweepUnmappedStateIds(pendingConfigContent.blocks());

        if (remapped > 0) {
            Pandorical.LOGGER.info("Remapped {} block state IDs to match server", remapped);
        } else {
            Pandorical.LOGGER.info("Block state IDs already match server — no remapping needed");
        }
    }

    /**
     * For server state ids the client could not rebuild: chunk palettes resolve ids through
     * {@code IdMapper.byIdOrThrow}, so an unmapped id fails the chunk decode. Stone, not air, so
     * the result is a wrong block rather than a hole.
     */
    private static BlockState fallbackState() {
        return Blocks.STONE.defaultBlockState();
    }

    private static String propertyNames(Block block) {
        List<String> names = new ArrayList<>();
        for (var prop : block.getStateDefinition().getProperties()) names.add(prop.getName());
        return names.toString();
    }

    /**
     * A null {@code stand} means {@link #fallbackState()}. Never {@code IdMapper.add}: Fabric's
     * StateIdTracker already added these states, so appending rewrites their reverse id and takes
     * a slot the server may have given another block.
     */
    private static void coverStateIdsWithFallback(SyncContentS2C.BlockEntry entry, BlockState stand, String reason) {
        BlockState state = stand != null ? stand : fallbackState();
        for (int serverId : entry.stateIds()) {
            mapWithoutStealingReverseId(state, serverId);
        }
        Pandorical.LOGGER.error(
            "Block '{}' could not be reproduced on the client ({}) — its {} state ID(s) now decode to {}. "
                + "The block will look wrong but will not corrupt neighbouring blocks. "
                + "Register it through PandoricalApi.content().registerBlock(...) with an explicit base block "
                + "so the client rebuilds the same state definition.",
            entry.id(), reason, entry.stateIds().size(),
            stand != null ? "its own default state" : "minecraft:stone");
    }

    /**
     * addMapping writes both directions; re-asserting the original id afterwards restores
     * {@code getId(state)} and leaves the new forward slot. The order matters.
     */
    private static void mapWithoutStealingReverseId(BlockState state, int serverId) {
        int originalId = Block.BLOCK_STATE_REGISTRY.getId(state);
        Block.BLOCK_STATE_REGISTRY.addMapping(state, serverId);
        if (originalId >= 0 && originalId != serverId) {
            Block.BLOCK_STATE_REGISTRY.addMapping(state, originalId);
        }
    }

    private static void sweepUnmappedStateIds(List<SyncContentS2C.BlockEntry> entries) {
        int holes = 0;
        String firstHoleBlock = null;
        for (SyncContentS2C.BlockEntry entry : entries) {
            for (int serverId : entry.stateIds()) {
                if (serverId < 0) continue;
                if (Block.BLOCK_STATE_REGISTRY.byId(serverId) != null) continue;
                mapWithoutStealingReverseId(fallbackState(), serverId);
                holes++;
                if (firstHoleBlock == null) firstHoleBlock = entry.id();
            }
        }
        if (holes > 0) {
            Pandorical.LOGGER.error(
                "Filled {} unmapped server block-state ID(s) with minecraft:stone (first offender: '{}'). "
                    + "Chunks using them would otherwise have decoded to null block states.",
                holes, firstHoleBlock);
        }
    }

}
