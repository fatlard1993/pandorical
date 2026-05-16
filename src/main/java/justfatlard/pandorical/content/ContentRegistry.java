package justfatlard.pandorical.content;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.api.BlockRegistration;
import justfatlard.pandorical.api.ContentApi;
import justfatlard.pandorical.api.ItemRegistration;
import justfatlard.pandorical.protocol.SyncAssetsConfigS2C;
import justfatlard.pandorical.protocol.SyncAssetsS2C;
import justfatlard.pandorical.protocol.SyncContentS2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPOutputStream;

/**
 * Server-side content registry. Stores block/item registrations and asset data.
 * Syncs to Pandorical clients on join.
 */
public class ContentRegistry implements ContentApi {
    private final Map<String, RegisteredBlock> blocks = new LinkedHashMap<>();
    private final Map<String, RegisteredItem> items = new LinkedHashMap<>();
    private final Map<String, byte[]> assets = new ConcurrentHashMap<>();
    private volatile List<SyncAssetsS2C> cachedAssetChunks = null;

    /** Vanilla item overrides: keyed by full item ID, e.g. "minecraft:rabbit_hide" */
    private final Map<String, justfatlard.pandorical.api.VanillaItemOverride> vanillaItemOverrides = new LinkedHashMap<>();

    /**
     * Set of mod namespaces that have registered content through Pandorical.
     * These namespaces should be excluded from Fabric's registry sync to allow
     * vanilla/unmodded clients to connect.
     */
    private static final Set<String> serverOnlyNamespaces = ConcurrentHashMap.newKeySet();

    public record RegisteredBlock(String id, BlockRegistration registration) {}
    public record RegisteredItem(String id, ItemRegistration registration) {}

    @Override
    public void registerBlock(String id, BlockRegistration registration) {
        blocks.put(id, new RegisteredBlock(id, registration));
        trackNamespace(id);
        Pandorical.LOGGER.info("Registered custom block: {}", id);
    }

    @Override
    public void registerItem(String id, ItemRegistration registration) {
        items.put(id, new RegisteredItem(id, registration));
        trackNamespace(id);
        Pandorical.LOGGER.info("Registered custom item: {}", id);
    }

    private void trackNamespace(String id) {
        int colonIndex = id.indexOf(':');
        if (colonIndex > 0) {
            String namespace = id.substring(0, colonIndex);
            if (!namespace.equals("minecraft")) {
                serverOnlyNamespaces.add(namespace);
                Pandorical.LOGGER.debug("Tracking server-only namespace: {}", namespace);
            }
        }
    }

    @Override
    public void registerAsset(String path, byte[] data) {
        assets.put(path, data);
        cachedAssetChunks = null; // invalidate cache
    }

    /**
     * Register an entire mod namespace as server-only for registry sync bypass.
     * Call this during onInitialize for mods that register custom content via Pandorical.
     */
    public void registerServerOnlyNamespace(String namespace) {
        if (!namespace.equals("minecraft")) {
            serverOnlyNamespaces.add(namespace);
            Pandorical.LOGGER.info("Registered server-only namespace: {}", namespace);
        }
    }

    /**
     * Check if a namespace is registered as server-only through Pandorical.
     */
    public static boolean isServerOnlyNamespace(String namespace) {
        return serverOnlyNamespaces.contains(namespace);
    }

    /**
     * Fast check for whether any server-only namespaces are registered.
     * Used in hot paths to avoid unnecessary work.
     */
    public static boolean hasServerOnlyNamespaces() {
        return !serverOnlyNamespaces.isEmpty();
    }

    // Cached unmodifiable view — safe because ConcurrentHashMap.KeySetView is thread-safe
    private static volatile Set<String> cachedUnmodifiableView = null;

    /**
     * Get all registered server-only namespaces.
     */
    public static Set<String> getServerOnlyNamespaces() {
        Set<String> cached = cachedUnmodifiableView;
        if (cached == null) {
            cached = java.util.Collections.unmodifiableSet(serverOnlyNamespaces);
            cachedUnmodifiableView = cached;
        }
        return cached;
    }

    @Override
    public void registerModAssets(String modId) {
        // Track the mod's namespace as server-only
        if (!modId.equals("minecraft")) {
            serverOnlyNamespaces.add(modId);
            Pandorical.LOGGER.debug("Tracking server-only namespace from mod assets: {}", modId);
        }

        // Scan the mod's jar for assets/{modId}/ files and register them.
        // Uses Fabric's resource loading to find the mod container.
        try {
            var modContainer = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer(modId);
            if (modContainer.isEmpty()) {
                Pandorical.LOGGER.warn("Mod '{}' not found — cannot register assets", modId);
                return;
            }

            var rootPaths = modContainer.get().getRootPaths();
            for (var root : rootPaths) {
                var assetsDir = root.resolve("assets").resolve(modId);
                if (!java.nio.file.Files.exists(assetsDir)) continue;

                try (var walk = java.nio.file.Files.walk(assetsDir)) {
                    walk.filter(java.nio.file.Files::isRegularFile).forEach(file -> {
                        try {
                            String relativePath = "assets/" + modId + "/" + assetsDir.relativize(file).toString();
                            byte[] data = java.nio.file.Files.readAllBytes(file);
                            registerAsset(relativePath, data);
                        } catch (IOException e) {
                            Pandorical.LOGGER.warn("Failed to read asset file {}: {}", file, e.getMessage());
                        }
                    });
                }
            }

            Pandorical.LOGGER.info("Registered assets for mod '{}' ({} total assets now)", modId, assets.size());
        } catch (Exception e) {
            Pandorical.LOGGER.warn("Failed to scan assets for mod {}: {}", modId, e.getMessage());
        }
    }

    @Override
    public void overrideVanillaItem(String vanillaItemId, justfatlard.pandorical.api.VanillaItemOverride override) {
        if (vanillaItemId == null || !vanillaItemId.contains(":")) {
            Pandorical.LOGGER.warn("Invalid vanilla item ID (must be namespace:path): {}", vanillaItemId);
            return;
        }
        vanillaItemOverrides.put(vanillaItemId, override);
        applyVanillaItemOverrideAssets(vanillaItemId, override);
        Pandorical.LOGGER.info("Registered vanilla item override: {}", vanillaItemId);
    }

    /**
     * Generate and register assets for a vanilla item override.
     *
     * Lang overrides are written into assets/pandorical/lang/en_us.json — lang files
     * from any namespace can carry keys for any other namespace, and the pandorical
     * namespace is definitely served by the VirtualResourcePack. Writing to
     * assets/minecraft/lang/en_us.json risks being shadowed by the built-in vanilla pack.
     *
     * Texture overrides are stored in the pandorical namespace and a generated model
     * references them there. Only the items/ redirect JSON must live in the item's
     * own namespace (e.g. assets/minecraft/items/rabbit_hide.json), which is a single
     * file and much less likely to be shadowed than texture atlas entries.
     */
    private void applyVanillaItemOverrideAssets(String vanillaItemId, justfatlard.pandorical.api.VanillaItemOverride override) {
        String[] parts = vanillaItemId.split(":", 2);
        String namespace = parts[0];
        String itemName = parts[1];
        // Flat key safe for use in file names: "minecraft:rabbit_hide" → "minecraft_rabbit_hide"
        String flatKey = namespace + "_" + itemName.replace('/', '_');

        if (override.hasTexture()) {
            // Store texture in pandorical namespace so VirtualResourcePack definitely serves it.
            registerAsset("assets/pandorical/textures/item/" + flatKey + ".png",
                override.getTextureData());

            // Auto-generate a flat item model in pandorical namespace referencing that texture.
            String autoModel = "{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": {\n    \"layer0\": \""
                + escapeJson("pandorical:item/" + flatKey) + "\"\n  }\n}\n";
            registerAsset("assets/pandorical/models/item/" + flatKey + ".json",
                autoModel.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Redirect the vanilla item's definition to our pandorical model (only if no
            // explicit model override is set — that case is handled below).
            if (!override.hasModel()) {
                String itemsJson = "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n    \"model\": \""
                    + escapeJson("pandorical:item/" + flatKey) + "\"\n  }\n}\n";
                registerAsset("assets/" + namespace + "/items/" + itemName + ".json",
                    itemsJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        }

        if (override.hasModel()) {
            String json = "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n    \"model\": \""
                + escapeJson(override.getModelPath()) + "\"\n  }\n}\n";
            registerAsset("assets/" + namespace + "/items/" + itemName + ".json",
                json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        if (override.hasName()) {
            rebuildVanillaLangFile();
        }
    }

    /**
     * Rebuild the merged lang file for all vanilla item name overrides.
     * All overrides (regardless of item namespace) are written into
     * assets/pandorical/lang/en_us.json so the VirtualResourcePack definitely
     * serves them — lang keys work across namespaces.
     */
    private void rebuildVanillaLangFile() {
        Map<String, String> entries = new LinkedHashMap<>();
        for (var e : vanillaItemOverrides.entrySet()) {
            if (!e.getValue().hasName()) continue;
            String[] parts = e.getKey().split(":", 2);
            // "minecraft:rabbit_hide" → "item.minecraft.rabbit_hide"
            // sub-paths like "foo/bar" → "item.minecraft.foo.bar"
            String langKey = "item." + parts[0] + "." + parts[1].replace('/', '.');
            entries.put(langKey, e.getValue().getDisplayName());
        }
        if (entries.isEmpty()) return;

        StringBuilder sb = new StringBuilder("{\n");
        var iter = entries.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            sb.append("  \"").append(escapeJson(entry.getKey()))
              .append("\": \"").append(escapeJson(entry.getValue())).append("\"");
            if (iter.hasNext()) sb.append(",");
            sb.append("\n");
        }
        sb.append("}");
        registerAsset("assets/pandorical/lang/en_us.json",
            sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Check if there's any content to sync.
     */
    public boolean hasContent() {
        if (!blocks.isEmpty() || !items.isEmpty()) return true;
        if (!vanillaItemOverrides.isEmpty()) return true;
        if (!assets.isEmpty()) return true;
        // Also check if there are any modded entries in the registries
        return hasServerOnlyNamespaces();
    }

    /**
     * Send content definitions to a player during PLAY phase.
     * In the new architecture, this only syncs non-registry content (screens, HUD, camera).
     * Block/item sync is handled in the CONFIGURATION phase via PandoricalSyncTask.
     */
    public void syncContentTo(ServerPlayer player) {
        List<SyncContentS2C.BlockEntry> blockEntries = buildBlockEntries();
        List<SyncContentS2C.ItemEntry> itemEntries = buildItemEntries();

        if (blockEntries.isEmpty() && itemEntries.isEmpty()) return;

        // Calculate asset chunk count before sending content so client knows what to expect
        int assetChunkCount = 0;
        if (!assets.isEmpty()) {
            try {
                List<SyncAssetsS2C> chunks = cachedAssetChunks;
                if (chunks == null) {
                    chunks = buildAssetChunks();
                    cachedAssetChunks = chunks;
                }
                assetChunkCount = chunks.size();
            } catch (IOException e) {
                Pandorical.LOGGER.error("Failed to build asset chunks for {}: {}", player.getName().getString(), e.getMessage());
            }
        }

        ServerPlayNetworking.send(player, new SyncContentS2C(blockEntries, itemEntries, assetChunkCount,
            buildEntityTypeEntries(), buildBlockEntityTypeEntries(), buildVillagerProfessionEntries(),
            buildPoiTypeEntries(), buildMenuTypeEntries(), buildRecipeBookCategoryEntries()));

        // Send assets if any
        if (assetChunkCount > 0) {
            sendAssets(player);
        }
    }

    private static final int CHUNK_SIZE = 900_000;

    /**
     * Bundle and send all registered assets as gzipped chunks.
     * Caches the compressed chunks so they aren't recompressed per player.
     */
    private void sendAssets(ServerPlayer player) {
        try {
            List<SyncAssetsS2C> chunks = cachedAssetChunks;
            if (chunks == null) {
                chunks = buildAssetChunks();
                cachedAssetChunks = chunks;
            }

            for (SyncAssetsS2C chunk : chunks) {
                ServerPlayNetworking.send(player, chunk);
            }

            Pandorical.LOGGER.debug("Sent {} asset chunks to {}",
                chunks.size(), player.getName().getString());
        } catch (IOException e) {
            Pandorical.LOGGER.error("Failed to send assets to {}: {}", player.getName().getString(), e.getMessage());
        }
    }

    private List<SyncAssetsS2C> buildAssetChunks() throws IOException {
        // Build format: [pathUTF][dataLen][data] repeated, then gzip
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        for (var entry : assets.entrySet()) {
            dos.writeUTF(entry.getKey());
            dos.writeInt(entry.getValue().length);
            dos.write(entry.getValue());
        }
        dos.flush();

        byte[] raw = baos.toByteArray();

        // Gzip
        ByteArrayOutputStream gzipBaos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(gzipBaos)) {
            gzos.write(raw);
        }
        byte[] compressed = gzipBaos.toByteArray();

        // Chunk into pieces
        int totalChunks = (compressed.length + CHUNK_SIZE - 1) / CHUNK_SIZE;
        List<SyncAssetsS2C> chunks = new ArrayList<>(totalChunks);

        for (int i = 0; i < totalChunks; i++) {
            int offset = i * CHUNK_SIZE;
            int len = Math.min(CHUNK_SIZE, compressed.length - offset);
            byte[] chunk = new byte[len];
            System.arraycopy(compressed, offset, chunk, 0, len);
            chunks.add(new SyncAssetsS2C(i, totalChunks, chunk));
        }

        return chunks;
    }

    /**
     * Build block entries by scanning all modded blocks in registries.
     * Used by both play-phase and config-phase sync.
     */
    public List<SyncContentS2C.BlockEntry> buildBlockEntries() {
        List<SyncContentS2C.BlockEntry> blockEntries = new java.util.ArrayList<>();
        for (var entry : net.minecraft.core.registries.BuiltInRegistries.BLOCK.entrySet()) {
            String namespace = entry.getKey().identifier().getNamespace();
            if (!isServerOnlyNamespace(namespace)) continue;

            var block = entry.getValue();
            String id = entry.getKey().identifier().toString();
            List<Integer> stateIds = new java.util.ArrayList<>();
            List<String> stateProps = new java.util.ArrayList<>();
            for (var prop : block.getStateDefinition().getProperties()) {
                String type = "i";
                if (prop instanceof net.minecraft.world.level.block.state.properties.BooleanProperty) type = "b";
                else if (prop instanceof net.minecraft.world.level.block.state.properties.EnumProperty) type = "e";
                // For enums, send the value names so the client can create matching properties
                if ("e".equals(type)) {
                    var names = new java.util.StringJoiner(",");
                    try {
                        var getNameMethod = net.minecraft.world.level.block.state.properties.Property.class
                            .getMethod("getName", Comparable.class);
                        for (var v : prop.getPossibleValues()) {
                            names.add((String) getNameMethod.invoke(prop, v));
                        }
                    } catch (Exception ex) {
                        for (var v : prop.getPossibleValues()) {
                            names.add(v.toString().toLowerCase(java.util.Locale.ROOT));
                        }
                    }
                    stateProps.add(prop.getName() + ":" + type + ":" + names.toString());
                } else if (prop instanceof net.minecraft.world.level.block.state.properties.IntegerProperty intProp) {
                    // Send min:max so the client creates IntegerProperty with the correct range.
                    // e.g. flower_amount [1,4] → "flower_amount:i:1:4" instead of "flower_amount:i:4"
                    // which would create [0,3] and fail to decode values like 4.
                    int min = intProp.getPossibleValues().stream().mapToInt(Integer::intValue).min().getAsInt();
                    int max = intProp.getPossibleValues().stream().mapToInt(Integer::intValue).max().getAsInt();
                    stateProps.add(prop.getName() + ":" + type + ":" + min + ":" + max);
                } else {
                    stateProps.add(prop.getName() + ":" + type + ":" + prop.getPossibleValues().size());
                }
            }
            for (var state : block.getStateDefinition().getPossibleStates()) {
                stateIds.add(net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.getId(state));
            }
            String baseBlockId = "";
            String modelId = "";
            var registered = blocks.get(id);
            if (registered != null) {
                baseBlockId = registered.registration().getBaseBlockId();
                modelId = registered.registration().getModelId();
            }

            // For auto-detected blocks (not registered via PandoricalApi), infer the
            // base block from the class hierarchy so the client can create the correct
            // block type (e.g., SlabBlock for slabs) and copy appropriate Properties.
            if (baseBlockId.isEmpty()) {
                baseBlockId = inferBaseBlockId(block);
            }

            // Serialize VoxelShapes for all states so the client has correct
            // collision and outline shapes regardless of block class.
            byte[] shapeData = serializeBlockShapes(block);

            blockEntries.add(new SyncContentS2C.BlockEntry(id, baseBlockId, stateProps, modelId, stateIds, shapeData));
        }
        return blockEntries;
    }

    /**
     * Serialize outline and collision VoxelShapes for all states of a block.
     * Format per state: [numOutlineBoxes:byte][boxes...][numCollisionBoxes:byte][boxes...]
     * Each box: [minX:float][minY:float][minZ:float][maxX:float][maxY:float][maxZ:float]
     */
    private static byte[] serializeBlockShapes(net.minecraft.world.level.block.Block block) {
        try {
            var baos = new ByteArrayOutputStream();
            var dos = new DataOutputStream(baos);
            var emptyGetter = net.minecraft.world.level.EmptyBlockGetter.INSTANCE;
            var origin = net.minecraft.core.BlockPos.ZERO;
            var ctx = net.minecraft.world.phys.shapes.CollisionContext.empty();

            for (var state : block.getStateDefinition().getPossibleStates()) {
                // Outline shape (selection box)
                var outline = state.getShape(emptyGetter, origin, ctx);
                writeShape(dos, outline);
                // Collision shape
                var collision = state.getCollisionShape(emptyGetter, origin, ctx);
                writeShape(dos, collision);
            }

            dos.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            Pandorical.LOGGER.warn("Failed to serialize shapes for {}: {}", block, e.getMessage());
            return new byte[0];
        }
    }

    private static void writeShape(DataOutputStream dos, net.minecraft.world.phys.shapes.VoxelShape shape) throws IOException {
        var boxes = shape.toAabbs();
        dos.writeByte(boxes.size());
        for (var box : boxes) {
            dos.writeFloat((float) box.minX);
            dos.writeFloat((float) box.minY);
            dos.writeFloat((float) box.minZ);
            dos.writeFloat((float) box.maxX);
            dos.writeFloat((float) box.maxY);
            dos.writeFloat((float) box.maxZ);
        }
    }

    /**
     * Build item entries by scanning all modded items in registries.
     * Used by both play-phase and config-phase sync.
     */
    public List<SyncContentS2C.ItemEntry> buildItemEntries() {
        List<SyncContentS2C.ItemEntry> itemEntries = new java.util.ArrayList<>();
        for (var entry : net.minecraft.core.registries.BuiltInRegistries.ITEM.entrySet()) {
            String namespace = entry.getKey().identifier().getNamespace();
            if (!isServerOnlyNamespace(namespace)) continue;

            var item = entry.getValue();
            String id = entry.getKey().identifier().toString();
            var registered = items.get(id);
            String modelId = registered != null ? registered.registration().getModelId() : "";
            boolean glint = registered != null && registered.registration().hasGlint();

            // Read durability from item components; use default item's max stack size
            int maxStack = item.getDefaultMaxStackSize();
            Integer maxDamageObj = item.components().get(net.minecraft.core.component.DataComponents.MAX_DAMAGE);
            int maxDamage = maxDamageObj != null ? maxDamageObj : 0;

            // Detect equipment slot from Equippable component
            String equipSlot = "";
            var equippable = item.components().get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
            if (equippable != null) {
                equipSlot = equippable.slot().getName();
            }

            // Detect tool type from item class or Tool component
            String toolType = inferToolType(item);

            itemEntries.add(new SyncContentS2C.ItemEntry(id, modelId, maxStack, maxDamage, glint, equipSlot, toolType));
        }
        return itemEntries;
    }

    /**
     * Infer tool type from item class hierarchy.
     * Returns: "sword", "pickaxe", "axe", "shovel", "hoe", or "" for non-tools.
     */
    private static String inferToolType(net.minecraft.world.item.Item item) {
        // In MC 26.1, tools are data-driven via the Tool component.
        // Check if the item has a Tool component to mark it as a tool.
        var tool = item.components().get(net.minecraft.core.component.DataComponents.TOOL);
        if (tool != null) return "tool";
        return "";
    }

    /**
     * Build config-phase asset chunks. Returns a list of SyncAssetsConfigS2C payloads.
     * Uses the same compression and chunking as play-phase, but with config-phase payload type.
     */
    /**
     * Auto-scan and register assets for all server-only mods that haven't
     * explicitly registered their assets via PandoricalApi.content().registerModAssets().
     */
    public void autoScanAllModAssets() {
        for (String namespace : serverOnlyNamespaces) {
            // Skip if this mod already registered assets explicitly
            boolean hasAssets = assets.keySet().stream().anyMatch(k -> k.startsWith("assets/" + namespace + "/"));
            if (hasAssets) continue;

            // Try to scan the mod's jar for assets
            var modContainer = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(namespace);
            if (modContainer.isEmpty()) continue;

            registerModAssets(namespace);
        }
    }

    public List<SyncAssetsConfigS2C> buildConfigAssetChunks() throws IOException {
        autoScanAllModAssets();
        if (assets.isEmpty()) return List.of();

        // Build format: [pathUTF][dataLen][data] repeated, then gzip
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        for (var entry : assets.entrySet()) {
            dos.writeUTF(entry.getKey());
            dos.writeInt(entry.getValue().length);
            dos.write(entry.getValue());
        }
        dos.flush();

        byte[] raw = baos.toByteArray();

        // Gzip
        ByteArrayOutputStream gzipBaos = new ByteArrayOutputStream();
        try (java.util.zip.GZIPOutputStream gzos = new java.util.zip.GZIPOutputStream(gzipBaos)) {
            gzos.write(raw);
        }
        byte[] compressed = gzipBaos.toByteArray();

        // Chunk into pieces
        int totalChunks = (compressed.length + CHUNK_SIZE - 1) / CHUNK_SIZE;
        List<SyncAssetsConfigS2C> chunks = new ArrayList<>(totalChunks);

        for (int i = 0; i < totalChunks; i++) {
            int offset = i * CHUNK_SIZE;
            int len = Math.min(CHUNK_SIZE, compressed.length - offset);
            byte[] chunk = new byte[len];
            System.arraycopy(compressed, offset, chunk, 0, len);
            chunks.add(new SyncAssetsConfigS2C(i, totalChunks, chunk));
        }

        return chunks;
    }

    /**
     * Scan a built-in registry for entries whose namespace is server-only.
     * Returns a list of identifier strings like "big-boats-justfatlard:ship".
     */
    private List<String> scanRegistry(net.minecraft.core.Registry<?> registry) {
        List<String> result = new java.util.ArrayList<>();
        for (var entry : registry.entrySet()) {
            String namespace = entry.getKey().identifier().getNamespace();
            if (!isServerOnlyNamespace(namespace)) continue;
            result.add(entry.getKey().identifier().toString());
        }
        return result;
    }

    /**
     * Build entity type entries by scanning for modded entity types.
     */
    public List<String> buildEntityTypeEntries() {
        return scanRegistry(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE);
    }

    /**
     * Build block entity type entries by scanning for modded block entity types.
     */
    public List<String> buildBlockEntityTypeEntries() {
        return scanRegistry(net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE);
    }

    /**
     * Build villager profession entries by scanning for modded villager professions.
     */
    public List<String> buildVillagerProfessionEntries() {
        return scanRegistry(net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION);
    }

    /**
     * Build POI type entries by scanning for modded POI types.
     */
    public List<String> buildPoiTypeEntries() {
        return scanRegistry(net.minecraft.core.registries.BuiltInRegistries.POINT_OF_INTEREST_TYPE);
    }

    /**
     * Build menu type entries by scanning for modded menu types.
     */
    public List<String> buildMenuTypeEntries() {
        return scanRegistry(net.minecraft.core.registries.BuiltInRegistries.MENU);
    }

    /**
     * Build recipe book category entries by scanning for modded recipe book categories.
     */
    public List<String> buildRecipeBookCategoryEntries() {
        return scanRegistry(net.minecraft.core.registries.BuiltInRegistries.RECIPE_BOOK_CATEGORY);
    }

    /**
     * Infer a base block ID from the block's class hierarchy.
     * Used for auto-detected blocks so the client can create the correct block type
     * and copy sensible BlockBehaviour.Properties.
     */
    private static String inferBaseBlockId(net.minecraft.world.level.block.Block block) {
        // Match by SoundType to get the right break/place/step sounds and material feel.
        // The client detects block type (slab, stair, etc.) from state properties independently.
        var sound = block.defaultBlockState().getSoundType();
        return inferBaseBlockFromSound(sound);
    }

    private static String inferBaseBlockFromSound(net.minecraft.world.level.block.SoundType sound) {
        if (sound == net.minecraft.world.level.block.SoundType.GRASS)   return "minecraft:grass_block";
        if (sound == net.minecraft.world.level.block.SoundType.GRAVEL)  return "minecraft:gravel";
        if (sound == net.minecraft.world.level.block.SoundType.WOOD)    return "minecraft:oak_planks";
        if (sound == net.minecraft.world.level.block.SoundType.STONE)   return "minecraft:stone";
        if (sound == net.minecraft.world.level.block.SoundType.METAL)   return "minecraft:iron_block";
        if (sound == net.minecraft.world.level.block.SoundType.GLASS)   return "minecraft:glass";
        if (sound == net.minecraft.world.level.block.SoundType.SAND)    return "minecraft:sand";
        if (sound == net.minecraft.world.level.block.SoundType.WOOL)    return "minecraft:white_wool";
        if (sound == net.minecraft.world.level.block.SoundType.SNOW)    return "minecraft:snow_block";
        // CLAY removed in MC 26.1
        if (sound == net.minecraft.world.level.block.SoundType.COPPER)  return "minecraft:copper_block";
        if (sound == net.minecraft.world.level.block.SoundType.CORAL_BLOCK)     return "minecraft:brain_coral_block";
        if (sound == net.minecraft.world.level.block.SoundType.NETHER_BRICKS)   return "minecraft:nether_bricks";
        if (sound == net.minecraft.world.level.block.SoundType.NYLIUM)          return "minecraft:crimson_nylium";
        if (sound == net.minecraft.world.level.block.SoundType.NETHERRACK)      return "minecraft:netherrack";
        if (sound == net.minecraft.world.level.block.SoundType.SOUL_SAND)       return "minecraft:soul_sand";
        if (sound == net.minecraft.world.level.block.SoundType.SOUL_SOIL)       return "minecraft:soul_soil";
        if (sound == net.minecraft.world.level.block.SoundType.BASALT)          return "minecraft:basalt";
        if (sound == net.minecraft.world.level.block.SoundType.MOSS)            return "minecraft:moss_block";
        if (sound == net.minecraft.world.level.block.SoundType.MUD)             return "minecraft:mud";
        if (sound == net.minecraft.world.level.block.SoundType.MUDDY_MANGROVE_ROOTS) return "minecraft:muddy_mangrove_roots";
        if (sound == net.minecraft.world.level.block.SoundType.ROOTED_DIRT)     return "minecraft:rooted_dirt";
        if (sound == net.minecraft.world.level.block.SoundType.PACKED_MUD)      return "minecraft:packed_mud";
        if (sound == net.minecraft.world.level.block.SoundType.DEEPSLATE)       return "minecraft:deepslate";
        if (sound == net.minecraft.world.level.block.SoundType.CALCITE)         return "minecraft:calcite";
        if (sound == net.minecraft.world.level.block.SoundType.TUFF)            return "minecraft:tuff";
        if (sound == net.minecraft.world.level.block.SoundType.DRIPSTONE_BLOCK) return "minecraft:dripstone_block";
        if (sound == net.minecraft.world.level.block.SoundType.AMETHYST)        return "minecraft:amethyst_block";
        // Fallback to stone — reasonable default for most blocks
        return "minecraft:stone";
    }

    public Map<String, RegisteredBlock> getBlocks() { return blocks; }
    public Map<String, RegisteredItem> getItems() { return items; }
}
