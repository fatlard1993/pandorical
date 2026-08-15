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
 * Client-side content registration: receives SyncContentS2C and SyncAssetsS2C,
 * registers the synced blocks/items into the (temporarily unfrozen) registries,
 * injects the VirtualResourcePack, triggers a resource reload, and acks with ContentReadyC2S.
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

    private static final int MAX_ASSET_BYTES = 50 * 1024 * 1024;
    private static final int MAX_SINGLE_ASSET = 10 * 1024 * 1024;
    private static final long SYNC_TIMEOUT_MS = 30_000;

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
    private static volatile boolean configPhaseSynced = false;

    // Config-phase state (separate from play-phase to avoid mixing)
    private static volatile SyncContentConfigS2C pendingConfigContent = null;
    private static final List<byte[]> configAssetChunks = new ArrayList<>();
    private static volatile int expectedConfigAssetChunks = -1;

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

        if (payload.chunkIndex() < 0 || payload.chunkIndex() >= payload.totalChunks()) {
            Pandorical.LOGGER.warn("Invalid asset chunk index {}/{}", payload.chunkIndex(), payload.totalChunks());
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
                    if (received > 0) unpackAssets();
                }
                forceFinalize();
            }
        }
    }

    // ==========================================================================
    // Configuration-phase handlers
    // ==========================================================================

    /** Runs on the network thread, never the render thread. */
    public static void handleConfigSyncContent(SyncContentConfigS2C payload) {
        pendingConfigContent = payload;
        expectedConfigAssetChunks = payload.expectedAssetChunks();
        syncStartTime = System.currentTimeMillis();
        syncing = true;
        Pandorical.LOGGER.info("Config phase: received {} blocks, {} items, expecting {} asset chunks",
            payload.blocks().size(), payload.items().size(), expectedConfigAssetChunks);

        tryFinalizeConfig();
    }

    /** Runs on the network thread, never the render thread. */
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
     * Runs on the network thread. Does not call addMapping; Fabric's
     * SynchronizeRegistriesTask assigns registry IDs, and block state IDs are
     * mapped later by {@link #remapBlockStateIds()}.
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

        if (expectedConfigAssetChunks > 0) {
            unpackConfigAssets();
        }

        // No global reconnect fast-path here: every register method below is
        // per-entry idempotent (an already-present id is reused/skipped, with
        // shape data reapplied for blocks), which handles reconnects AND the
        // case a former any-present short-circuit fatally mishandled: a mod
        // installed on BOTH client and server (e.g. pinata) pre-registers its
        // own entries at client startup, which made a first connection look
        // like a reconnect and skipped registering every server-only mod's
        // entries, so Fabric's registry sync then rejected the join with
        // hundreds of unknown entries.
        {
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

        // Resource pack injection happens later, once the Minecraft client instance exists;
        // config phase doesn't have it yet.

        // The ack lets the server complete PandoricalSyncTask.
        ClientConfigurationNetworking.send(new ContentReadyConfigC2S());
        Pandorical.LOGGER.info("Config phase: sent ContentReadyConfigC2S acknowledgment");
    }

    /**
     * Config-phase variant of registerBlock: leaves block state IDs unassigned so
     * {@link #remapBlockStateIds()} can map them to the server's IDs at JOIN time.
     */
    private static void registerBlockConfig(SyncContentS2C.BlockEntry entry) {
        try {
            Identifier id = Identifier.tryParse(entry.id());
            if (id == null) {
                Pandorical.LOGGER.warn("Config phase: invalid block ID: '{}'", entry.id());
                return;
            }

            // On reconnect the block survives from the previous session: track it and
            // apply fresh shape data rather than re-registering.
            if (BuiltInRegistries.BLOCK.containsKey(id)) {
                Block existing = BuiltInRegistries.BLOCK.getValue(id);
                registeredBlocks.put(id, existing);
                DynamicBlock.applyShapeData(existing, entry.shapeData());
                Pandorical.LOGGER.debug("Config phase: block {} already registered — reusing", entry.id());
                return;
            }

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

            Block baseBlock = baseId != null ? BuiltInRegistries.BLOCK.getValue(baseId) : null;
            List<net.minecraft.world.level.block.state.properties.Property<?>> stateProps = new java.util.ArrayList<>();
            for (String propSpec : entry.stateProperties()) {
                // Wire format: "name:type:valuesOrCount" where type is b=boolean, i=integer,
                // e=enum (comma-separated names); integers use "name:i:min:max" (split(":",3)
                // leaves parts[2]="min:max"); legacy form is "name:valueCount".
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
                        } catch (NumberFormatException ignored) {
                            Pandorical.LOGGER.warn("[pandorical] Malformed state prop range '{}' for block '{}', defaulting to 0", parts[2], entry.id());
                        }
                    } else {
                        try { valueCount = Integer.parseInt(parts[2]); } catch (NumberFormatException ignored) {
                            Pandorical.LOGGER.warn("[pandorical] Malformed state prop range '{}' for block '{}', defaulting to 0", parts[2], entry.id());
                        }
                    }
                } else if (parts.length == 2) {
                    try { valueCount = Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) {
                        Pandorical.LOGGER.warn("[pandorical] Malformed state prop range '{}' for block '{}', defaulting to 0", parts[1], entry.id());
                    }
                }
                var prop = DynamicBlock.resolveProperty(propName, baseBlock, valueCount, intMin, propType, enumValues);
                if (prop != null) {
                    stateProps.add(prop);
                } else {
                    Pandorical.LOGGER.warn("Config phase: unknown state property '{}' (type={}, values={}) for block '{}'",
                        propName, propType, valueCount, entry.id());
                }
            }

            Block block = createBlock(props, stateProps, baseBlock, entry.stateProperties());
            Registry.register(BuiltInRegistries.BLOCK, id, block);
            registeredBlocks.put(id, block);

            DynamicBlock.applyShapeData(block, entry.shapeData());

            // Do NOT add states to BLOCK_STATE_REGISTRY here. They get the server's IDs
            // in remapBlockStateIds(), which runs synchronously at JOIN time, before any
            // chunks are decoded.

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

    public static boolean wasConfigPhaseSynced() {
        return configPhaseSynced;
    }

    // ==========================================================================
    // Play-phase handlers (kept as fallback for non-registry content)
    // ==========================================================================

    private static boolean allAssetsReceived() {
        if (expectedAssetChunks == 0) return true;
        if (expectedAssetChunks < 0) return false; // content packet hasn't arrived yet
        return assetChunks.size() == expectedAssetChunks
            && assetChunks.stream().noneMatch(Objects::isNull);
    }

    private static void unpackAssets() {
        try {
            long totalSize = assetChunks.stream().filter(Objects::nonNull).mapToLong(c -> c.length).sum();
            if (totalSize > MAX_ASSET_BYTES) {
                Pandorical.LOGGER.error("Asset data too large: {} bytes (max {})", totalSize, MAX_ASSET_BYTES);
                return;
            }

            ByteArrayOutputStream assembled = new ByteArrayOutputStream();
            for (byte[] chunk : assetChunks) {
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
                        Pandorical.LOGGER.error("Decompressed asset data exceeds {}MB limit — aborting",
                            MAX_ASSET_BYTES / 1024 / 1024);
                        return;
                    }
                    decompressedBaos.write(buf, 0, read);
                }
            }

            // Wire format: [pathUTF][dataLen][data] repeated
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

        // If config-phase already registered blocks/items, skip registry work;
        // play-phase then only handles resource pack injection and non-registry features.
        if (!configPhaseSynced) {
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
                // Don't re-freeze: 26.1 validates that tags aren't present before freezing,
                // and client tags are already loaded from initial startup.
            }
        } else {
            Pandorical.LOGGER.debug("Play phase: skipping block/item/stub registration — already done in config phase");
        }

        injectResourcePack();

        ClientPlayNetworking.send(new ContentReadyC2S());
    }

    /** Called from both config-phase finalize (deferred) and play-phase finalize. */
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

        var namespaces = virtualPack.getNamespaces(net.minecraft.server.packs.PackType.CLIENT_RESOURCES);
        Pandorical.LOGGER.info("Virtual pack contains {} namespaces: {}", namespaces.size(), namespaces);
        virtualPack.debugLangFiles();

        // Registering a RepositorySource (rather than adding the pack once) keeps the
        // virtual pack included in every future resource reload.
        try {
            var packRepo = client.getResourcePackRepository();
            var sourcesField = net.minecraft.server.packs.repository.PackRepository.class.getDeclaredField("sources");
            sourcesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            var sources = (java.util.Set<net.minecraft.server.packs.repository.RepositorySource>) sourcesField.get(packRepo);

            var pandoricalSource = new net.minecraft.server.packs.repository.RepositorySource() {
                @Override
                public void loadPacks(java.util.function.Consumer<net.minecraft.server.packs.repository.Pack> consumer) {
                    Pandorical.LOGGER.debug("PackRepository is loading packs — providing Pandorical virtual pack");
                    var supplier = new net.minecraft.server.packs.repository.Pack.ResourcesSupplier() {
                        @Override
                        public net.minecraft.server.packs.PackMetadataResources openMetadata(net.minecraft.server.packs.PackLocationInfo info) { return virtualPack; }
                        @Override
                        public java.util.stream.Stream<net.minecraft.server.packs.PackResources> openResources(net.minecraft.server.packs.PackLocationInfo info, net.minecraft.server.packs.repository.Pack.Metadata metadata) { return java.util.stream.Stream.of(virtualPack); }
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

            var mutableSources = new java.util.LinkedHashSet<>(sources);
            mutableSources.add(pandoricalSource);
            sourcesField.set(packRepo, mutableSources);

            Pandorical.LOGGER.info("Added Pandorical virtual pack source to PackRepository ({} sources total) — triggering reload",
                mutableSources.size());
        } catch (Exception e) {
            Pandorical.LOGGER.error("Failed to add virtual pack source: {}", e.getMessage(), e);
            return;
        }

        // Both registrations must precede the reload: tint providers so tinted models
        // render correctly, and creative tab items because tab contents are rebuilt
        // during reload.
        registerBlockColors(client);
        registerCreativeTabItems();

        client.reloadResourcePacks().thenRun(() -> {
            // Force all chunks to re-render so they pick up the newly loaded block models.
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

            var lang = net.minecraft.locale.Language.getInstance();
            String test = lang.getOrDefault("block.dirt-slab-justfatlard.dirt_slab", "NOT_FOUND");
            Pandorical.LOGGER.info("Lang test: block.dirt-slab-justfatlard.dirt_slab = '{}'", test);
        });
    }

    private static void registerCreativeTabItems() {
        if (pendingConfigContent == null) return;
        try {
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
                    combat.add(item);
                } else if (!entry.toolType().isEmpty()) {
                    tools.add(item);
                } else if (item instanceof net.minecraft.world.item.BlockItem blockItem) {
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

    public static void injectResourcePackAndReRender(Minecraft client) {
        injectResourcePack();
    }

    /**
     * Register block color providers for dynamically registered blocks: tint sources are
     * copied from the base block so biome-sensitive colours (foliage, grass, water) are
     * inherited; blocks with no base tints fall back to the grass tint. Registering a
     * tint on a block whose model has no tintindex faces is harmless.
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

                // getTintSources() returns the sources registered for the base block's
                // default state, so the biome behaviour matches exactly.
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

            // On reconnect, reuse the existing block
            if (BuiltInRegistries.BLOCK.containsKey(id)) {
                Block existing = BuiltInRegistries.BLOCK.getValue(id);
                registeredBlocks.put(id, existing);
                DynamicBlock.applyShapeData(existing, entry.shapeData());
                return;
            }

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

            Block baseBlock = baseId != null ? BuiltInRegistries.BLOCK.getValue(baseId) : null;
            List<net.minecraft.world.level.block.state.properties.Property<?>> stateProps = new java.util.ArrayList<>();
            for (String propSpec : entry.stateProperties()) {
                // Wire format: same as in registerBlockConfig above.
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
                        } catch (NumberFormatException ignored) {
                            Pandorical.LOGGER.warn("[pandorical] Malformed state prop range '{}' for block '{}', defaulting to 0", parts[2], entry.id());
                        }
                    } else {
                        try { valueCount = Integer.parseInt(parts[2]); } catch (NumberFormatException ignored) {
                            Pandorical.LOGGER.warn("[pandorical] Malformed state prop range '{}' for block '{}', defaulting to 0", parts[2], entry.id());
                        }
                    }
                } else if (parts.length == 2) {
                    try { valueCount = Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) {
                        Pandorical.LOGGER.warn("[pandorical] Malformed state prop range '{}' for block '{}', defaulting to 0", parts[1], entry.id());
                    }
                }
                var prop = DynamicBlock.resolveProperty(propName, baseBlock, valueCount, intMin, propType, enumValues);
                if (prop != null) {
                    stateProps.add(prop);
                } else {
                    Pandorical.LOGGER.warn("Unknown state property '{}' (type={}, values={}) for block '{}'", propName, propType, valueCount, entry.id());
                }
            }

            Block block = createBlock(props, stateProps, baseBlock, entry.stateProperties());
            Registry.register(BuiltInRegistries.BLOCK, id, block);
            registeredBlocks.put(id, block);

            DynamicBlock.applyShapeData(block, entry.shapeData());

            // Register block states at the exact IDs the server uses
            var possibleStates = block.getStateDefinition().getPossibleStates();
            if (entry.stateIds().size() == possibleStates.size()) {
                for (int i = 0; i < possibleStates.size(); i++) {
                    Block.BLOCK_STATE_REGISTRY.addMapping(possibleStates.get(i), entry.stateIds().get(i));
                }
            } else {
                // Fallback: append sequentially (IDs may not match the server's)
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

            // Durability and stack size are mutually exclusive in MC
            if (entry.maxDamage() > 0) {
                props.durability(entry.maxDamage());
            } else {
                props.stacksTo(entry.maxStackSize());
            }

            if (!entry.equipSlot().isEmpty()) {
                var slot = net.minecraft.world.entity.EquipmentSlot.byName(entry.equipSlot());
                props.equippable(slot);
            }

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
     * Pick the Block subclass from the base block type or state property patterns:
     * the right class (e.g. SlabBlock for slabs) provides collision shapes, placement,
     * and interaction logic that a plain Block/DynamicBlock cannot.
     */
    private static Block createBlock(BlockBehaviour.Properties props,
                                     List<net.minecraft.world.level.block.state.properties.Property<?>> stateProps,
                                     Block baseBlock, List<String> rawPropSpecs) {
        // All dynamic blocks need noOcclusion: the shape isn't known at construction time
        // (server-provided VoxelShapes arrive after), and without it MC assumes full-cube
        // occlusion and incorrectly culls adjacent block faces.
        props.noOcclusion();

        boolean isSlab = baseBlock instanceof net.minecraft.world.level.block.SlabBlock || isSlabFromProperties(rawPropSpecs);

        if (isSlab) {
            // Filter out type and waterlogged; SlabBlock adds those itself
            List<net.minecraft.world.level.block.state.properties.Property<?>> extraProps = new java.util.ArrayList<>();
            for (var prop : stateProps) {
                String name = prop.getName();
                if (!name.equals("type") && !name.equals("waterlogged")) {
                    extraProps.add(prop);
                }
            }

            if (extraProps.isEmpty()) {
                return new net.minecraft.world.level.block.SlabBlock(props);
            } else {
                // Slab with extra properties (snowy, moisture, etc.)
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
    // Stub registration: each registers inert entries so Fabric's registry sync
    // doesn't reject the server's IDs for registry types the client can't fully
    // reconstruct. Each returns the number of entries registered.
    // ==========================================================================

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
                    net.minecraft.network.chat.Component.literal(idStr),
                    holder -> false,  // heldJobSite: matches nothing
                    holder -> false,  // acquirableJobSite: matches nothing
                    com.google.common.collect.ImmutableSet.of(),
                    com.google.common.collect.ImmutableSet.of(),
                    null,  // workSound
                    new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>()
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

    /** Unfrozen registries need intrusive holders created manually before register(). */
    @SuppressWarnings("unchecked")
    private static <T> void registerWithHolder(Registry<T> registry, Identifier id, T entry) {
        if (registry instanceof MappedRegistry<T> mapped) {
            mapped.createIntrusiveHolder(entry);
        }
        Registry.register(registry, id, entry);
    }

    /**
     * Remap block state IDs to the server's IDs, after Fabric's registry sync completes
     * (play-phase JOIN). Block.BLOCK_STATE_REGISTRY is an IdMapper outside Fabric's
     * registry sync, so config-phase IDs may differ from the server's.
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
                    // addMapping, not add: states may not be in the registry yet
                    Block.BLOCK_STATE_REGISTRY.addMapping(possibleStates.get(i), serverId);
                    remapped++;
                }
            } catch (Exception e) {
                Pandorical.LOGGER.warn("Failed to remap block state IDs for {}: {}", entry.id(), e.getMessage());
            }
        }

        if (remapped > 0) {
            Pandorical.LOGGER.info("Remapped {} block state IDs to match server", remapped);
            // One-block diagnostic dump (dirt_slab as the sample)
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
                    Item testItem = BuiltInRegistries.ITEM.getValue(testId);
                    Pandorical.LOGGER.info("DIAG: item={} class={} maxStack={}",
                        testId, testItem != null ? testItem.getClass().getName() : "null",
                        testItem != null ? testItem.getDefaultMaxStackSize() : -1);
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
                                var modelId = Identifier.fromNamespaceAndPath("dirt-slab-justfatlard", "models/block/rooted_dirt_slab.json");
                    var modelResource = virtualPack.getResource(net.minecraft.server.packs.PackType.CLIENT_RESOURCES, modelId);
                    Pandorical.LOGGER.info("DIAG: virtualPack has model file? {}", modelResource != null);
                    for (var state : testBlock.getStateDefinition().getPossibleStates()) {
                        Pandorical.LOGGER.info("DIAG: state.toString()='{}' regId={}", state.toString(), Block.BLOCK_STATE_REGISTRY.getId(state));
                        break;
                    }
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
