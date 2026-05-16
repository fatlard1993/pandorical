package justfatlard.pandorical.client.content;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.protocol.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Client-side content registration.
 * Receives SyncContentS2C and SyncAssetsS2C, then:
 * 1. Unfreezes registries
 * 2. Registers blocks and items (cloning properties from base blocks)
 * 3. Re-freezes registries
 * 4. Injects VirtualResourcePack with synced assets
 * 5. Triggers resource reload
 * 6. Sends ContentReadyC2S
 */
public class ContentManager {
    private static final VirtualResourcePack virtualPack = new VirtualResourcePack();
    private static volatile SyncContentS2C pendingContent = null;
    private static final List<byte[]> assetChunks = new ArrayList<>();
    private static volatile int expectedAssetChunks = -1; // -1 = not yet known
    private static volatile boolean contentRegistered = false;
    private static long syncStartTime = 0;

    // Blocks registered during the current sync, for reliable BlockItem creation.
    // Registry lookups on unfrozen registries may not find recently registered entries.
    private static final Map<Identifier, Block> registeredBlocks = new HashMap<>();

    // Safety limits
    private static final int MAX_ASSET_BYTES = 50 * 1024 * 1024; // 50MB max
    private static final int MAX_SINGLE_ASSET = 10 * 1024 * 1024; // 10MB per file
    private static final long SYNC_TIMEOUT_MS = 30_000; // 30 second timeout

    public static void reset() {
        pendingContent = null;
        assetChunks.clear();
        expectedAssetChunks = -1;
        contentRegistered = false;
        syncStartTime = 0;
        syncing = false;
        configPhaseSynced = false;
        registeredBlocks.clear();
        pendingConfigContent = null;
        configAssetChunks.clear();
        expectedConfigAssetChunks = -1;
        virtualPack.clear();
    }

    private static volatile boolean syncing = false;
    /** True if content was synced during configuration phase (not play phase). */
    private static volatile boolean configPhaseSynced = false;

    // Config-phase state (separate from play-phase to avoid mixing)
    private static volatile SyncContentConfigS2C pendingConfigContent = null;
    private static final List<byte[]> configAssetChunks = new ArrayList<>();
    private static volatile int expectedConfigAssetChunks = -1;

    /** True while content is being synced from the server. */
    public static boolean isSyncing() { return syncing && !contentRegistered; }

    /** Progress 0.0-1.0 based on asset chunks received. */
    public static float getSyncProgress() {
        if (expectedAssetChunks <= 0) return 0f;
        int received = 0;
        for (byte[] chunk : assetChunks) {
            if (chunk != null) received++;
        }
        return (float) received / expectedAssetChunks;
    }

    /** Human-readable sync status. */
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

    /**
     * Handle content definition payload.
     */
    public static void handleSyncContent(SyncContentS2C payload) {
        pendingContent = payload;
        expectedAssetChunks = payload.expectedAssetChunks();
        syncStartTime = System.currentTimeMillis();
        syncing = true;
        Pandorical.LOGGER.info("Received content sync: {} blocks, {} items, expecting {} asset chunks",
            payload.blocks().size(), payload.items().size(), expectedAssetChunks);

        // If no assets expected (or assets already arrived), finalize
        tryFinalize();
    }

    /**
     * Handle asset chunk payload.
     */
    public static void handleSyncAssets(SyncAssetsS2C payload) {
        if (contentRegistered) {
            Pandorical.LOGGER.warn("Received asset chunk after content already registered — ignoring");
            return;
        }

        expectedAssetChunks = payload.totalChunks();
        if (syncStartTime == 0) syncStartTime = System.currentTimeMillis();

        // Validate chunk index
        if (payload.chunkIndex() < 0 || payload.chunkIndex() >= payload.totalChunks()) {
            Pandorical.LOGGER.warn("Invalid asset chunk index {}/{}", payload.chunkIndex(), payload.totalChunks());
            return;
        }

        // Store chunk at correct index
        while (assetChunks.size() <= payload.chunkIndex()) {
            assetChunks.add(null);
        }
        assetChunks.set(payload.chunkIndex(), payload.data());

        Pandorical.LOGGER.debug("Received asset chunk {}/{}", payload.chunkIndex() + 1, payload.totalChunks());

        // Check if all chunks received
        tryFinalize();
    }

    /**
     * Called periodically to check for timeout on asset reassembly.
     */
    public static void tick() {
        if (syncStartTime > 0 && !contentRegistered) {
            if (System.currentTimeMillis() - syncStartTime > SYNC_TIMEOUT_MS) {
                Pandorical.LOGGER.warn("Content sync timed out after {}ms — finalizing with available data",
                    SYNC_TIMEOUT_MS);
                // Force finalize with what we have
                if (expectedAssetChunks > 0) {
                    long received = assetChunks.stream().filter(Objects::nonNull).count();
                    Pandorical.LOGGER.warn("Received {}/{} asset chunks before timeout", received, expectedAssetChunks);
                    if (received > 0) unpackAssets();
                }
                forceFinalize();
            }
        }
    }

    // ==========================================================================
    // Configuration-phase handlers
    // ==========================================================================

    /**
     * Handle content definition payload during CONFIGURATION phase.
     * Called on the network thread — NOT the render thread.
     */
    public static void handleConfigSyncContent(SyncContentConfigS2C payload) {
        pendingConfigContent = payload;
        expectedConfigAssetChunks = payload.expectedAssetChunks();
        syncStartTime = System.currentTimeMillis();
        syncing = true;
        Pandorical.LOGGER.info("Config phase: received {} blocks, {} items, expecting {} asset chunks",
            payload.blocks().size(), payload.items().size(), expectedConfigAssetChunks);

        tryFinalizeConfig();
    }

    /**
     * Handle asset chunk payload during CONFIGURATION phase.
     * Called on the network thread — NOT the render thread.
     */
    public static void handleConfigSyncAssets(SyncAssetsConfigS2C payload) {
        if (configPhaseSynced) {
            Pandorical.LOGGER.warn("Config phase: received asset chunk after already synced — ignoring");
            return;
        }

        expectedConfigAssetChunks = payload.totalChunks();

        if (payload.chunkIndex() < 0 || payload.chunkIndex() >= payload.totalChunks()) {
            Pandorical.LOGGER.warn("Config phase: invalid asset chunk index {}/{}", payload.chunkIndex(), payload.totalChunks());
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
        if (configPhaseSynced) return;
        if (!allConfigAssetsReceived()) return;
        forceFinalizeConfig();
    }

    /**
     * Finalize config-phase sync: register blocks/items, unpack assets, send ack.
     * This runs on the NETWORK THREAD during config phase.
     * We do NOT call addMapping — Fabric's SynchronizeRegistriesTask will assign IDs.
     * We DO call Block.BLOCK_STATE_REGISTRY.add() so states exist for Fabric to sync.
     */
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

        // Unpack assets first (into virtual pack)
        if (expectedConfigAssetChunks > 0) {
            unpackConfigAssets();
        }

        // Check if this is a reconnect — blocks already registered from previous session.
        // On reconnect, skip all registry manipulation to avoid corrupting Fabric's state.
        // Just repopulate tracking maps and apply shape data.
        boolean isReconnect = content.blocks().stream().anyMatch(entry -> {
            Identifier id = Identifier.tryParse(entry.id());
            return id != null && BuiltInRegistries.BLOCK.containsKey(id);
        });

        if (isReconnect) {
            Pandorical.LOGGER.info("Config phase: reconnect detected — reusing {} existing blocks, {} items",
                content.blocks().size(), content.items().size());
            for (SyncContentS2C.BlockEntry entry : content.blocks()) {
                Identifier id = Identifier.tryParse(entry.id());
                if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
                    Block existing = BuiltInRegistries.BLOCK.getValue(id);
                    registeredBlocks.put(id, existing);
                    DynamicBlock.applyShapeData(existing, entry.shapeData());
                }
            }
        } else {
            // First connection — register blocks/items/stubs
            unfreezeRegistry(BuiltInRegistries.BLOCK);
            unfreezeRegistry(BuiltInRegistries.ITEM);
            unfreezeRegistry(BuiltInRegistries.ENTITY_TYPE);
            unfreezeRegistry(BuiltInRegistries.BLOCK_ENTITY_TYPE);
            unfreezeRegistry(BuiltInRegistries.VILLAGER_PROFESSION);
            unfreezeRegistry(BuiltInRegistries.POINT_OF_INTEREST_TYPE);
            unfreezeRegistry(BuiltInRegistries.MENU);
            unfreezeRegistry(BuiltInRegistries.RECIPE_BOOK_CATEGORY);

            try {
                for (SyncContentS2C.BlockEntry entry : content.blocks()) {
                    registerBlockConfig(entry);
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

                Pandorical.LOGGER.info("Config phase: registered {} blocks, {} items, and {} additional registry stubs",
                    content.blocks().size(), content.items().size(), stubCount);
            } finally {
                try {
                    freezeRegistry(BuiltInRegistries.BLOCK);
                    freezeRegistry(BuiltInRegistries.ITEM);
                    freezeRegistry(BuiltInRegistries.ENTITY_TYPE);
                    freezeRegistry(BuiltInRegistries.BLOCK_ENTITY_TYPE);
                    freezeRegistry(BuiltInRegistries.VILLAGER_PROFESSION);
                    freezeRegistry(BuiltInRegistries.POINT_OF_INTEREST_TYPE);
                    freezeRegistry(BuiltInRegistries.MENU);
                    freezeRegistry(BuiltInRegistries.RECIPE_BOOK_CATEGORY);
                } catch (Exception e) {
                    Pandorical.LOGGER.warn("Failed to re-freeze registries: {}", e.getMessage());
                }
            }
        }

        // Note: resource pack injection happens later when Minecraft client is fully available
        // (during play phase or via a scheduled task). Config phase doesn't have the client instance ready.

        // Send acknowledgment to server so it can complete PandoricalSyncTask
        ClientConfigurationNetworking.send(new ContentReadyConfigC2S());
        Pandorical.LOGGER.info("Config phase: sent ContentReadyConfigC2S acknowledgment");
    }

    /**
     * Register a block during config phase.
     * Same as registerBlock but does NOT call addMapping — instead calls
     * Block.BLOCK_STATE_REGISTRY.add() so the state exists in the registry
     * for Fabric's sync to assign the authoritative ID.
     */
    private static void registerBlockConfig(SyncContentS2C.BlockEntry entry) {
        try {
            Identifier id = Identifier.tryParse(entry.id());
            if (id == null) {
                Pandorical.LOGGER.warn("Config phase: invalid block ID: '{}'", entry.id());
                return;
            }

            // On reconnect, blocks may already be registered from the previous session.
            // Track the existing block and apply fresh shape data — don't re-register.
            if (BuiltInRegistries.BLOCK.containsKey(id)) {
                Block existing = BuiltInRegistries.BLOCK.getValue(id);
                registeredBlocks.put(id, existing);
                DynamicBlock.applyShapeData(existing, entry.shapeData());
                Pandorical.LOGGER.debug("Config phase: block {} already registered — reusing", entry.id());
                return;
            }

            // Clone properties from base block
            Identifier baseId = Identifier.tryParse(entry.baseBlockId());
            BlockBehaviour.Properties props;
            if (baseId != null) {
                Block baseBlock = BuiltInRegistries.BLOCK.getValue(baseId);
                if (baseBlock != null) {
                    props = BlockBehaviour.Properties.ofFullCopy(baseBlock);
                } else {
                    Pandorical.LOGGER.warn("Config phase: base block '{}' not found for '{}' — using defaults",
                        entry.baseBlockId(), entry.id());
                    props = BlockBehaviour.Properties.of();
                }
            } else {
                props = BlockBehaviour.Properties.of();
            }

            ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
            props.setId(key);

            // Resolve state properties
            Block baseBlock = baseId != null ? BuiltInRegistries.BLOCK.getValue(baseId) : null;
            List<net.minecraft.world.level.block.state.properties.Property<?>> stateProps = new java.util.ArrayList<>();
            for (String propSpec : entry.stateProperties()) {
                // Format: "name:type:valuesOrCount"
                // type: b=boolean, i=integer, e=enum(comma-separated names)
                // or legacy "name:valueCount"
                // For integers: new format "name:i:min:max" (split(":",3) gives parts[2]="min:max")
                String[] parts = propSpec.split(":", 3);
                String propName = parts[0];
                String propType = "i";
                int valueCount = -1;
                int intMin = 0;
                String enumValues = null;
                if (parts.length == 3) {
                    propType = parts[1];
                    if ("e".equals(propType)) {
                        enumValues = parts[2];
                        valueCount = parts[2].split(",").length;
                    } else if ("i".equals(propType) && parts[2].contains(":")) {
                        // New format: "min:max"
                        String[] minMax = parts[2].split(":", 2);
                        try {
                            intMin = Integer.parseInt(minMax[0]);
                            int intMax = Integer.parseInt(minMax[1]);
                            valueCount = intMax - intMin + 1;
                        } catch (NumberFormatException ignored) {}
                    } else {
                        try { valueCount = Integer.parseInt(parts[2]); } catch (NumberFormatException ignored) {}
                    }
                } else if (parts.length == 2) {
                    try { valueCount = Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) {}
                }
                var prop = DynamicBlock.resolveProperty(propName, baseBlock, valueCount, intMin, propType, enumValues);
                if (prop != null) {
                    stateProps.add(prop);
                } else {
                    Pandorical.LOGGER.warn("Config phase: unknown state property '{}' (type={}, values={}) for block '{}'",
                        propName, propType, valueCount, entry.id());
                }
            }

            // Create the appropriate block class based on base block type or state properties.
            // Using the correct class gives proper collision shapes, placement behavior, etc.
            Block block = createBlock(props, stateProps, baseBlock, entry.stateProperties());
            Registry.register(BuiltInRegistries.BLOCK, id, block);
            registeredBlocks.put(id, block);

            // Apply server-provided VoxelShapes for correct collision and selection
            DynamicBlock.applyShapeData(block, entry.shapeData());

            // Do NOT add states to BLOCK_STATE_REGISTRY here.
            // States will be added with correct server IDs in remapBlockStateIds()
            // which runs synchronously at JOIN time, before any chunks are decoded.

            Pandorical.LOGGER.debug("Config phase: registered block {} (base: {}, class: {}, states: {})",
                entry.id(), entry.baseBlockId(), block.getClass().getSimpleName(),
                block.getStateDefinition().getPossibleStates().size());
        } catch (Exception e) {
            Pandorical.LOGGER.error("Config phase: failed to register block {}: {}", entry.id(), e.getMessage(), e);
        }
    }

    private static void unpackConfigAssets() {
        try {
            long totalSize = configAssetChunks.stream().filter(Objects::nonNull).mapToLong(c -> c.length).sum();
            if (totalSize > MAX_ASSET_BYTES) {
                Pandorical.LOGGER.error("Config phase: asset data too large: {} bytes (max {})", totalSize, MAX_ASSET_BYTES);
                return;
            }

            ByteArrayOutputStream assembled = new ByteArrayOutputStream();
            for (byte[] chunk : configAssetChunks) {
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
                        Pandorical.LOGGER.error("Config phase: decompressed data exceeds {}MB limit", MAX_ASSET_BYTES / 1024 / 1024);
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
                    Pandorical.LOGGER.error("Config phase: asset '{}' has invalid size: {} bytes", path, len);
                    break;
                }
                byte[] data = new byte[len];
                dis.readFully(data);
                virtualPack.addResource(path, data);
                count++;
            }

            Pandorical.LOGGER.info("Config phase: unpacked {} assets", count);
        } catch (IOException e) {
            Pandorical.LOGGER.error("Config phase: failed to unpack assets: {}", e.getMessage(), e);
        }
    }

    /**
     * Returns true if content was synced during the configuration phase.
     * Used to determine if play-phase sync should be skipped for blocks/items.
     */
    public static boolean wasConfigPhaseSynced() {
        return configPhaseSynced;
    }

    // ==========================================================================
    // Play-phase handlers (kept as fallback for non-registry content)
    // ==========================================================================

    private static boolean allAssetsReceived() {
        if (expectedAssetChunks == 0) return true; // No assets expected
        if (expectedAssetChunks < 0) return false; // Not yet known (content packet hasn't arrived)
        return assetChunks.size() == expectedAssetChunks
            && assetChunks.stream().noneMatch(Objects::isNull);
    }

    private static void unpackAssets() {
        try {
            // Check total size before assembling
            long totalSize = assetChunks.stream().filter(Objects::nonNull).mapToLong(c -> c.length).sum();
            if (totalSize > MAX_ASSET_BYTES) {
                Pandorical.LOGGER.error("Asset data too large: {} bytes (max {})", totalSize, MAX_ASSET_BYTES);
                return;
            }

            // Reassemble chunks
            ByteArrayOutputStream assembled = new ByteArrayOutputStream();
            for (byte[] chunk : assetChunks) {
                if (chunk != null) assembled.write(chunk);
            }

            // Decompress with size limit
            byte[] compressed = assembled.toByteArray();
            ByteArrayOutputStream decompressedBaos = new ByteArrayOutputStream();
            try (GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
                byte[] buf = new byte[8192];
                int read;
                long total = 0;
                while ((read = gzis.read(buf)) != -1) {
                    total += read;
                    if (total > MAX_ASSET_BYTES) {
                        Pandorical.LOGGER.error("Decompressed asset data exceeds {}MB limit — aborting",
                            MAX_ASSET_BYTES / 1024 / 1024);
                        return;
                    }
                    decompressedBaos.write(buf, 0, read);
                }
            }

            // Parse: [pathUTF][dataLen][data] repeated
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(decompressedBaos.toByteArray()));
            int count = 0;
            while (dis.available() > 0) {
                String path = dis.readUTF();
                int len = dis.readInt();
                if (len < 0 || len > MAX_SINGLE_ASSET) {
                    Pandorical.LOGGER.error("Asset '{}' has invalid size: {} bytes", path, len);
                    break;
                }
                byte[] data = new byte[len];
                dis.readFully(data);
                virtualPack.addResource(path, data);
                count++;
            }

            Pandorical.LOGGER.info("Unpacked {} assets from server", count);
        } catch (IOException e) {
            Pandorical.LOGGER.error("Failed to unpack assets: {}", e.getMessage(), e);
        }
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

        // If config-phase already registered blocks/items, skip registry work.
        // Play-phase only needs to handle resource pack injection and non-registry features.
        if (!configPhaseSynced) {
            // Unfreeze registries
            unfreezeRegistry(BuiltInRegistries.BLOCK);
            unfreezeRegistry(BuiltInRegistries.ITEM);
            unfreezeRegistry(BuiltInRegistries.ENTITY_TYPE);
            unfreezeRegistry(BuiltInRegistries.BLOCK_ENTITY_TYPE);
            unfreezeRegistry(BuiltInRegistries.VILLAGER_PROFESSION);
            unfreezeRegistry(BuiltInRegistries.POINT_OF_INTEREST_TYPE);
            unfreezeRegistry(BuiltInRegistries.MENU);
            unfreezeRegistry(BuiltInRegistries.RECIPE_BOOK_CATEGORY);

            try {
                for (SyncContentS2C.BlockEntry entry : content.blocks()) {
                    registerBlock(entry);
                }
                for (SyncContentS2C.ItemEntry entry : content.items()) {
                    registerItem(entry);
                }

                // Register stubs for additional registry types (play-phase fallback)
                int stubCount = 0;
                stubCount += registerEntityTypeStubs(content.entityTypes());
                stubCount += registerBlockEntityTypeStubs(content.blockEntityTypes());
                stubCount += registerVillagerProfessionStubs(content.villagerProfessions());
                stubCount += registerPoiTypeStubs(content.poiTypes());
                stubCount += registerMenuTypeStubs(content.menuTypes());
                stubCount += registerRecipeBookCategoryStubs(content.recipeBookCategories());

                Pandorical.LOGGER.info("Play phase fallback: registered {} blocks, {} items, and {} stubs on client",
                    content.blocks().size(), content.items().size(), stubCount);
            } finally {
                // Don't re-freeze — 26.1 validates tags aren't present before freezing,
                // but client tags are already loaded from initial startup.
            }
        } else {
            Pandorical.LOGGER.debug("Play phase: skipping block/item/stub registration — already done in config phase");
        }

        // Inject virtual resource pack and trigger reload
        injectResourcePack();

        ClientPlayNetworking.send(new ContentReadyC2S());
    }

    /**
     * Inject the virtual resource pack if it has resources.
     * Called from both config-phase finalize (deferred) and play-phase finalize.
     */
    public static void injectResourcePack() {
        if (!virtualPack.hasResources()) {
            Pandorical.LOGGER.warn("Virtual pack has no resources — skipping injection");
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            Pandorical.LOGGER.warn("Minecraft client is null — cannot inject resource pack");
            return;
        }

        // Log what's in the virtual pack for diagnostics
        var namespaces = virtualPack.getNamespaces(net.minecraft.server.packs.PackType.CLIENT_RESOURCES);
        Pandorical.LOGGER.info("Virtual pack contains {} namespaces: {}", namespaces.size(), namespaces);
        virtualPack.debugLangFiles();

        // Add a custom RepositorySource to the PackRepository that provides our virtual pack.
        // This ensures our pack is included in every future resource reload.
        try {
            var packRepo = client.getResourcePackRepository();
            // Get the 'sources' set from PackRepository
            var sourcesField = net.minecraft.server.packs.repository.PackRepository.class.getDeclaredField("sources");
            sourcesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            var sources = (java.util.Set<net.minecraft.server.packs.repository.RepositorySource>) sourcesField.get(packRepo);

            // Add our source if not already present
            var pandoricalSource = new net.minecraft.server.packs.repository.RepositorySource() {
                @Override
                public void loadPacks(java.util.function.Consumer<net.minecraft.server.packs.repository.Pack> consumer) {
                    Pandorical.LOGGER.debug("PackRepository is loading packs — providing Pandorical virtual pack");
                    var supplier = new net.minecraft.server.packs.repository.Pack.ResourcesSupplier() {
                        @Override
                        public net.minecraft.server.packs.PackResources openPrimary(net.minecraft.server.packs.PackLocationInfo info) { return virtualPack; }
                        @Override
                        public net.minecraft.server.packs.PackResources openFull(net.minecraft.server.packs.PackLocationInfo info, net.minecraft.server.packs.repository.Pack.Metadata metadata) { return virtualPack; }
                    };

                    var metadata = new net.minecraft.server.packs.repository.Pack.Metadata(
                        net.minecraft.network.chat.Component.literal("Pandorical synced assets"),
                        net.minecraft.server.packs.repository.PackCompatibility.COMPATIBLE,
                        net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS,
                        List.of()
                    );

                    var pack = new net.minecraft.server.packs.repository.Pack(
                        virtualPack.location(),
                        supplier,
                        metadata,
                        new net.minecraft.server.packs.PackSelectionConfig(true, net.minecraft.server.packs.repository.Pack.Position.TOP, false)
                    );
                    consumer.accept(pack);
                }
            };

            // Make the set mutable if needed
            var mutableSources = new java.util.LinkedHashSet<>(sources);
            mutableSources.add(pandoricalSource);
            sourcesField.set(packRepo, mutableSources);

            Pandorical.LOGGER.info("Added Pandorical virtual pack source to PackRepository ({} sources total) — triggering reload",
                mutableSources.size());
        } catch (Exception e) {
            Pandorical.LOGGER.error("Failed to add virtual pack source: {}", e.getMessage(), e);
            return;
        }

        // Register block/item color providers for dynamic blocks that need biome tinting.
        // This must happen before the resource reload so tinted models render correctly.
        registerBlockColors(client);

        // Register creative tab items BEFORE reload — tab contents are rebuilt during reload
        registerCreativeTabItems();

        // Trigger full resource reload — now our pack will be included
        client.reloadResourcePacks().thenRun(() -> {
            // After reload completes, force all chunks to re-render
            // so they pick up the newly loaded block models
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.levelRenderer != null) {
                mc.levelRenderer.allChanged();
                Pandorical.LOGGER.info("Resource reload complete — forced chunk re-render");
            }

            // Verify the pack was actually loaded
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

            // Verify language loaded
            var lang = net.minecraft.locale.Language.getInstance();
            String test = lang.getOrDefault("block.dirt-slab-justfatlard.dirt_slab", "NOT_FOUND");
            Pandorical.LOGGER.info("Lang test: block.dirt-slab-justfatlard.dirt_slab = '{}'", test);
        });
    }

    /**
     * Add all dynamically registered items to creative tabs so they appear in creative inventory.
     */
    private static void registerCreativeTabItems() {
        if (pendingConfigContent == null) return;
        try {
            // Categorize items by type for correct creative tab placement
            List<Item> buildingBlocks = new java.util.ArrayList<>();
            List<Item> combat = new java.util.ArrayList<>();
            List<Item> tools = new java.util.ArrayList<>();
            List<Item> ingredients = new java.util.ArrayList<>();
            List<Item> naturalBlocks = new java.util.ArrayList<>();
            List<Item> functional = new java.util.ArrayList<>();

            for (SyncContentS2C.ItemEntry entry : pendingConfigContent.items()) {
                Identifier id = Identifier.tryParse(entry.id());
                if (id == null) continue;
                Item item = BuiltInRegistries.ITEM.getValue(id);
                if (item == null || item == net.minecraft.world.item.Items.AIR) continue;

                if (!entry.equipSlot().isEmpty()) {
                    // Armor → Combat
                    combat.add(item);
                } else if (!entry.toolType().isEmpty()) {
                    // Tools/weapons → Tools & Utilities
                    tools.add(item);
                } else if (item instanceof net.minecraft.world.item.BlockItem blockItem) {
                    var block = blockItem.getBlock();
                    // Slabs, stairs, fences, walls → Building Blocks
                    // Plants, crops, dirt variants → Natural Blocks
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
                        // Other blocks (mailbox, table, etc.) → Functional
                        functional.add(item);
                    }
                } else {
                    // Everything else → Ingredients
                    ingredients.add(item);
                }
            }

            var tabEvents = net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.class;
            registerForTab(net.minecraft.world.item.CreativeModeTabs.BUILDING_BLOCKS, buildingBlocks);
            registerForTab(net.minecraft.world.item.CreativeModeTabs.COMBAT, combat);
            registerForTab(net.minecraft.world.item.CreativeModeTabs.TOOLS_AND_UTILITIES, tools);
            registerForTab(net.minecraft.world.item.CreativeModeTabs.NATURAL_BLOCKS, naturalBlocks);
            registerForTab(net.minecraft.world.item.CreativeModeTabs.FUNCTIONAL_BLOCKS, functional);
            registerForTab(net.minecraft.world.item.CreativeModeTabs.INGREDIENTS, ingredients);

            int total = buildingBlocks.size() + combat.size() + tools.size()
                + naturalBlocks.size() + functional.size() + ingredients.size();
            Pandorical.LOGGER.info("Registered {} items for creative tabs (building={}, combat={}, tools={}, natural={}, functional={}, ingredients={})",
                total, buildingBlocks.size(), combat.size(), tools.size(),
                naturalBlocks.size(), functional.size(), ingredients.size());
        } catch (Exception e) {
            Pandorical.LOGGER.warn("Failed to register creative tab items: {}", e.getMessage());
        }
    }

    private static void registerForTab(net.minecraft.resources.ResourceKey<net.minecraft.world.item.CreativeModeTab> tabKey,
                                        List<Item> items) {
        if (items.isEmpty()) return;
        net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents
            .modifyOutputEvent(tabKey)
            .register((output) -> {
                for (Item item : items) {
                    output.accept(item);
                }
            });
    }

    /**
     * Inject resource pack and force re-render after reload completes.
     */
    public static void injectResourcePackAndReRender(Minecraft client) {
        injectResourcePack();
    }

    /**
     * Register block color providers for dynamically registered blocks.
     * If the block declares a baseBlockId, we copy the base block's tint sources so that
     * biome-sensitive colours (foliage, grass, water, …) are inherited correctly.
     * For blocks with no base, or whose base has no registered tints, we fall back to
     * the grass tint as a reasonable default.
     * Registering a tint on a block whose model has no tintindex faces is harmless.
     */
    private static void registerBlockColors(Minecraft client) {
        if (pendingConfigContent == null) return;

        var blockColors = client.getBlockColors();
        var grassTint = List.of(net.minecraft.client.color.block.BlockTintSources.grass());
        int registered = 0;

        for (SyncContentS2C.BlockEntry entry : pendingConfigContent.blocks()) {
            try {
                Identifier id = Identifier.tryParse(entry.id());
                if (id == null) continue;

                Block block = BuiltInRegistries.BLOCK.getValue(id);
                if (block == null) continue;

                // Resolve tint sources from the base block when available.
                // getTintSources() returns the sources registered for that block's
                // default state, so we get exactly the same biome behaviour.
                List<net.minecraft.client.color.block.BlockTintSource> tintSources = null;

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

                if (tintSources == null) {
                    tintSources = grassTint;
                }

                blockColors.register(tintSources, block);
                registered++;
            } catch (Exception e) {
                Pandorical.LOGGER.warn("Failed to register color provider for {}: {}", entry.id(), e.getMessage());
            }
        }

        Pandorical.LOGGER.info("Registered block color providers for {} blocks", registered);
    }

    private static void registerBlock(SyncContentS2C.BlockEntry entry) {
        try {
            Identifier id = Identifier.tryParse(entry.id());
            if (id == null) {
                Pandorical.LOGGER.warn("Invalid block ID: '{}'", entry.id());
                return;
            }

            // On reconnect, reuse existing block
            if (BuiltInRegistries.BLOCK.containsKey(id)) {
                Block existing = BuiltInRegistries.BLOCK.getValue(id);
                registeredBlocks.put(id, existing);
                DynamicBlock.applyShapeData(existing, entry.shapeData());
                return;
            }

            // Clone properties from base block
            Identifier baseId = Identifier.tryParse(entry.baseBlockId());
            BlockBehaviour.Properties props;
            if (baseId != null) {
                Block baseBlock = BuiltInRegistries.BLOCK.getValue(baseId);
                if (baseBlock != null) {
                    props = BlockBehaviour.Properties.ofFullCopy(baseBlock);
                } else {
                    Pandorical.LOGGER.warn("Base block '{}' not found for '{}' — using defaults",
                        entry.baseBlockId(), entry.id());
                    props = BlockBehaviour.Properties.of();
                }
            } else {
                props = BlockBehaviour.Properties.of();
            }

            ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
            props.setId(key);

            // Resolve state properties from the base block or well-known names
            Block baseBlock = baseId != null ? BuiltInRegistries.BLOCK.getValue(baseId) : null;
            List<net.minecraft.world.level.block.state.properties.Property<?>> stateProps = new java.util.ArrayList<>();
            for (String propSpec : entry.stateProperties()) {
                // Format: "name:type:valuesOrCount"
                // type: b=boolean, i=integer, e=enum(comma-separated names)
                // For integers: new format "name:i:min:max"
                String[] parts = propSpec.split(":", 3);
                String propName = parts[0];
                String propType = "i";
                int valueCount = -1;
                int intMin = 0;
                String enumValues = null;
                if (parts.length == 3) {
                    propType = parts[1];
                    if ("e".equals(propType)) {
                        enumValues = parts[2];
                        valueCount = parts[2].split(",").length;
                    } else if ("i".equals(propType) && parts[2].contains(":")) {
                        String[] minMax = parts[2].split(":", 2);
                        try {
                            intMin = Integer.parseInt(minMax[0]);
                            int intMax = Integer.parseInt(minMax[1]);
                            valueCount = intMax - intMin + 1;
                        } catch (NumberFormatException ignored) {}
                    } else {
                        try { valueCount = Integer.parseInt(parts[2]); } catch (NumberFormatException ignored) {}
                    }
                } else if (parts.length == 2) {
                    try { valueCount = Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) {}
                }
                var prop = DynamicBlock.resolveProperty(propName, baseBlock, valueCount, intMin, propType, enumValues);
                if (prop != null) {
                    stateProps.add(prop);
                } else {
                    Pandorical.LOGGER.warn("Unknown state property '{}' (type={}, values={}) for block '{}'", propName, propType, valueCount, entry.id());
                }
            }

            // Create the appropriate block class based on base block type or state properties.
            Block block = createBlock(props, stateProps, baseBlock, entry.stateProperties());
            Registry.register(BuiltInRegistries.BLOCK, id, block);
            registeredBlocks.put(id, block);

            // Apply server-provided VoxelShapes
            DynamicBlock.applyShapeData(block, entry.shapeData());

            // Register block states at the exact IDs the server uses
            var possibleStates = block.getStateDefinition().getPossibleStates();
            if (entry.stateIds().size() == possibleStates.size()) {
                for (int i = 0; i < possibleStates.size(); i++) {
                    Block.BLOCK_STATE_REGISTRY.addMapping(possibleStates.get(i), entry.stateIds().get(i));
                }
            } else {
                // Fallback — append sequentially (IDs may not match server)
                for (BlockState state : possibleStates) {
                    Block.BLOCK_STATE_REGISTRY.add(state);
                }
                Pandorical.LOGGER.warn("Block {} state count mismatch: server={}, client={}",
                    entry.id(), entry.stateIds().size(), possibleStates.size());
            }

            Pandorical.LOGGER.debug("Registered client block: {} (base: {}, states: {}, ids: {})",
                entry.id(), entry.baseBlockId(), possibleStates.size(), entry.stateIds());
        } catch (Exception e) {
            Pandorical.LOGGER.error("Failed to register block {}: {}", entry.id(), e.getMessage(), e);
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

            // Apply durability or stack size (mutually exclusive in MC)
            if (entry.maxDamage() > 0) {
                props.durability(entry.maxDamage());
            } else {
                props.stacksTo(entry.maxStackSize());
            }

            // Apply equipment slot if present (armor)
            if (!entry.equipSlot().isEmpty()) {
                var slot = net.minecraft.world.entity.EquipmentSlot.byName(entry.equipSlot());
                props.equippable(slot);
            }

            // If there's a block with the same ID, create a BlockItem.
            Item item;
            Block block = registeredBlocks.get(id);
            if (block == null) {
                block = BuiltInRegistries.BLOCK.getValue(id);
                if (block == net.minecraft.world.level.block.Blocks.AIR) block = null;
            }
            if (block != null) {
                item = new net.minecraft.world.item.BlockItem(block, props.useBlockDescriptionPrefix());
            } else {
                item = new Item(props);
            }
            registerWithHolder(BuiltInRegistries.ITEM, id, item);

            // Log description ID for diagnosis
            if (entry.id().contains("dirt_slab") && !entry.id().contains("coarse")) {
                Pandorical.LOGGER.info("ITEM DIAG: {} class={} descId='{}' block={}",
                    entry.id(), item.getClass().getSimpleName(),
                    item.getDescriptionId(), block != null ? block.getClass().getSimpleName() : "null");
            }
        } catch (Exception e) {
            Pandorical.LOGGER.error("Failed to register item {}: {}", entry.id(), e.getMessage(), e);
        }
    }

    /**
     * Create the appropriate Block subclass based on the base block type or state property patterns.
     * Using the correct class (e.g., SlabBlock for slabs) provides proper collision shapes,
     * placement behavior, and interaction logic that a plain Block/DynamicBlock cannot.
     */
    private static Block createBlock(BlockBehaviour.Properties props,
                                     List<net.minecraft.world.level.block.state.properties.Property<?>> stateProps,
                                     Block baseBlock, List<String> rawPropSpecs) {
        // All dynamic blocks need noOcclusion — we don't know the shape at construction time.
        // Server-provided VoxelShapes are applied after construction. Without this, MC assumes
        // full-cube occlusion and incorrectly culls adjacent block faces.
        props.noOcclusion();

        boolean isSlab = baseBlock instanceof net.minecraft.world.level.block.SlabBlock || isSlabFromProperties(rawPropSpecs);

        if (isSlab) {
            // Filter out type and waterlogged — SlabBlock adds those itself
            List<net.minecraft.world.level.block.state.properties.Property<?>> extraProps = new java.util.ArrayList<>();
            for (var prop : stateProps) {
                String name = prop.getName();
                if (!name.equals("type") && !name.equals("waterlogged")) {
                    extraProps.add(prop);
                }
            }

            if (extraProps.isEmpty()) {
                // Pure slab — use vanilla SlabBlock directly
                return new net.minecraft.world.level.block.SlabBlock(props);
            } else {
                // Slab with extra properties (snowy, moisture, etc.) — use DynamicSlabBlock
                return DynamicSlabBlock.create(props, extraProps);
            }
        }

        if (stateProps.isEmpty()) {
            return new Block(props);
        }
        return DynamicBlock.create(props, stateProps);
    }

    /**
     * Detect slab blocks from their state property specifications.
     * Slabs have a "type" enum property with values "bottom", "top", "double".
     */
    private static boolean isSlabFromProperties(List<String> rawPropSpecs) {
        for (String spec : rawPropSpecs) {
            if (spec.startsWith("type:e:") && spec.contains("bottom") && spec.contains("top") && spec.contains("double")) {
                return true;
            }
        }
        return false;
    }

    // ==========================================================================
    // Stub registration for additional registry types
    // ==========================================================================

    /**
     * Register stub EntityType entries so Fabric's registry sync doesn't reject them.
     * Returns the number of entries registered.
     */
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
                EntityType<?> stub = EntityType.Builder.createNothing(MobCategory.MISC)
                    .noSave().noSummon().sized(0, 0)
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

    /**
     * Register stub BlockEntityType entries so Fabric's registry sync doesn't reject them.
     * Returns the number of entries registered.
     */
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
                BlockEntityType<?> stub = new BlockEntityType<>((pos, state) -> null, java.util.Set.of());
                registerWithHolder(BuiltInRegistries.BLOCK_ENTITY_TYPE, id, stub);
                count++;
                Pandorical.LOGGER.debug("Config phase: registered stub block entity type: {}", idStr);
            } catch (Exception e) {
                Pandorical.LOGGER.error("Config phase: failed to register stub block entity type {}: {}", idStr, e.getMessage(), e);
            }
        }
        return count;
    }

    /**
     * Register stub VillagerProfession entries so Fabric's registry sync doesn't reject them.
     * Returns the number of entries registered.
     */
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
                // Public record constructor: (name, heldJobSite, acquirableJobSite, requestedItems, secondaryPoi, workSound, tradeSetsByLevel)
                VillagerProfession stub = new VillagerProfession(
                    net.minecraft.network.chat.Component.literal(idStr),
                    holder -> false,  // heldJobSite — matches nothing
                    holder -> false,  // acquirableJobSite — matches nothing
                    com.google.common.collect.ImmutableSet.of(),
                    com.google.common.collect.ImmutableSet.of(),
                    null,  // workSound — no sound
                    new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>()
                );
                // Create intrusive holder first, then register
                
                registerWithHolder(BuiltInRegistries.VILLAGER_PROFESSION, id, stub);
                count++;
                Pandorical.LOGGER.debug("Config phase: registered stub villager profession: {}", idStr);
            } catch (Exception e) {
                Pandorical.LOGGER.error("Config phase: failed to register stub villager profession {}: {}", idStr, e.getMessage(), e);
            }
        }
        return count;
    }

    /**
     * Register stub PoiType entries so Fabric's registry sync doesn't reject them.
     * Returns the number of entries registered.
     */
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
                // Public record constructor: (matchingStates, maxTickets, validRange)
                PoiType stub = new PoiType(java.util.Set.of(), 0, 0);
                registerWithHolder(BuiltInRegistries.POINT_OF_INTEREST_TYPE, id, stub);
                count++;
                Pandorical.LOGGER.debug("Config phase: registered stub POI type: {}", idStr);
            } catch (Exception e) {
                Pandorical.LOGGER.error("Config phase: failed to register stub POI type {}: {}", idStr, e.getMessage(), e);
            }
        }
        return count;
    }

    /**
     * Register stub MenuType entries so Fabric's registry sync doesn't reject them.
     * Returns the number of entries registered.
     */
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

    /**
     * Register stub RecipeBookCategory entries so Fabric's registry sync doesn't reject them.
     * Returns the number of entries registered.
     */
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

    private static void unfreezeRegistry(Registry<?> registry) {
        if (registry instanceof MappedRegistry<?> mapped) {
            mapped.frozen = false;
            // Restore intrusive holder cache so new entries can be registered
            try {
                var field = MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");
                field.setAccessible(true);
                if (field.get(mapped) == null) {
                    field.set(mapped, new java.util.IdentityHashMap<>());
                }
            } catch (Exception e) {
                Pandorical.LOGGER.warn("Could not restore intrusive holder cache for {}", registry, e);
            }
        }
    }

    private static void freezeRegistry(Registry<?> registry) {
        if (registry instanceof MappedRegistry<?> mapped) {
            try {
                // Clear allTags to avoid "tags already present before freezing" error
                var allTagsField = MappedRegistry.class.getDeclaredField("allTags");
                allTagsField.setAccessible(true);
                allTagsField.set(mapped, null);
            } catch (Exception e) {
                // Field might not exist in this MC version
            }
            try {
                mapped.freeze();
            } catch (Exception e) {
                Pandorical.LOGGER.debug("Could not freeze registry {}: {}", registry, e.getMessage());
            }
        }
    }

    /**
     * Register an entry into a registry with proper intrusive holder creation.
     * This handles the case where registries have been unfrozen and need
     * intrusive holders created manually.
     */
    @SuppressWarnings("unchecked")
    private static <T> void registerWithHolder(Registry<T> registry, Identifier id, T entry) {
        if (registry instanceof MappedRegistry<T> mapped) {
            mapped.createIntrusiveHolder(entry);
        }
        Registry.register(registry, id, entry);
    }

    /**
     * Remap block state IDs to match the server's IDs.
     * Called after Fabric's registry sync has completed (during play-phase JOIN).
     *
     * Block.BLOCK_STATE_REGISTRY is an IdMapper that doesn't participate in Fabric's
     * registry sync. The IDs assigned during config-phase registration via add() may
     * differ from the server's IDs. This method uses the server's state IDs from the
     * content sync to re-map entries to the correct positions.
     */
    public static void remapBlockStateIds() {
        if (pendingConfigContent == null) return;

        int remapped = 0;
        for (SyncContentS2C.BlockEntry entry : pendingConfigContent.blocks()) {
            try {
                Identifier id = Identifier.tryParse(entry.id());
                if (id == null) continue;

                Block block = BuiltInRegistries.BLOCK.getValue(id);
                if (block == null) continue;

                var possibleStates = block.getStateDefinition().getPossibleStates();
                if (entry.stateIds().size() != possibleStates.size()) {
                    Pandorical.LOGGER.warn("Block state count mismatch for {}: server={}, client={}",
                        entry.id(), entry.stateIds().size(), possibleStates.size());
                    continue;
                }

                for (int i = 0; i < possibleStates.size(); i++) {
                    int serverId = entry.stateIds().get(i);
                    // Always use addMapping with server's ID — states may not be in the registry yet
                    Block.BLOCK_STATE_REGISTRY.addMapping(possibleStates.get(i), serverId);
                    remapped++;
                }
            } catch (Exception e) {
                Pandorical.LOGGER.warn("Failed to remap block state IDs for {}: {}", entry.id(), e.getMessage());
            }
        }

        if (remapped > 0) {
            Pandorical.LOGGER.info("Remapped {} block state IDs to match server", remapped);
            // Thorough diagnostics for one block
            for (SyncContentS2C.BlockEntry entry : pendingConfigContent.blocks()) {
                if (entry.id().contains("dirt_slab") && !entry.id().contains("coarse") && !entry.stateIds().isEmpty()) {
                    Identifier testId = Identifier.tryParse(entry.id());
                    Block testBlock = BuiltInRegistries.BLOCK.getValue(testId);
                    Pandorical.LOGGER.info("DIAG: block {} class={}", entry.id(), testBlock.getClass().getName());
                    Pandorical.LOGGER.info("DIAG: stateDefinition properties={}", testBlock.getStateDefinition().getProperties());
                    Pandorical.LOGGER.info("DIAG: possibleStates count={}", testBlock.getStateDefinition().getPossibleStates().size());
                    Pandorical.LOGGER.info("DIAG: defaultState={}", testBlock.defaultBlockState());
                    int sid = entry.stateIds().get(0);
                    var resolved = Block.BLOCK_STATE_REGISTRY.byId(sid);
                    Pandorical.LOGGER.info("DIAG: stateId[0]={} resolves to {}", sid, resolved);
                    // Check if the item exists
                    Item testItem = BuiltInRegistries.ITEM.getValue(testId);
                    Pandorical.LOGGER.info("DIAG: item={} class={} maxStack={}",
                        testId, testItem != null ? testItem.getClass().getName() : "null",
                        testItem != null ? testItem.getDefaultMaxStackSize() : -1);
                    // Check if the virtual pack has the blockstate file
                    var bsId = Identifier.fromNamespaceAndPath("dirt-slab-justfatlard", "blockstates/rooted_dirt_slab.json");
                    var bsResource = virtualPack.getResource(net.minecraft.server.packs.PackType.CLIENT_RESOURCES, bsId);
                    Pandorical.LOGGER.info("DIAG: virtualPack has blockstate file? {}", bsResource != null);
                    if (bsResource != null) {
                        try {
                            var is = bsResource.get();
                            var bytes = is.readAllBytes();
                            Pandorical.LOGGER.info("DIAG: blockstate file size={} content='{}'", bytes.length, new String(bytes).substring(0, Math.min(200, bytes.length)));
                        } catch (Exception ex) { Pandorical.LOGGER.warn("DIAG: couldn't read blockstate", ex); }
                    }
                    // Also check model
                    var modelId = Identifier.fromNamespaceAndPath("dirt-slab-justfatlard", "models/block/rooted_dirt_slab.json");
                    var modelResource = virtualPack.getResource(net.minecraft.server.packs.PackType.CLIENT_RESOURCES, modelId);
                    Pandorical.LOGGER.info("DIAG: virtualPack has model file? {}", modelResource != null);
                    // Check block state toString representation (how MC builds variant keys)
                    for (var state : testBlock.getStateDefinition().getPossibleStates()) {
                        Pandorical.LOGGER.info("DIAG: state.toString()='{}' regId={}", state.toString(), Block.BLOCK_STATE_REGISTRY.getId(state));
                        break; // Just first one
                    }
                    // Check each state's variant string
                    for (var state : testBlock.getStateDefinition().getPossibleStates()) {
                        var props = new StringBuilder();
                        for (var prop : state.getProperties()) {
                            if (props.length() > 0) props.append(",");
                            props.append(prop.getName()).append("=").append(state.getValue(prop));
                        }
                        int stateRegId = Block.BLOCK_STATE_REGISTRY.getId(state);
                        Pandorical.LOGGER.info("DIAG: state variant='{}' regId={}", props, stateRegId);
                    }
                    break;
                }
            }
        } else {
            Pandorical.LOGGER.info("Block state IDs already match server — no remapping needed");
        }
    }

    public static VirtualResourcePack getVirtualPack() {
        return virtualPack;
    }
}
