package justfatlard.pandorical.content;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.api.BlockRegistration;
import justfatlard.pandorical.api.ContentApi;
import justfatlard.pandorical.api.ItemRegistration;
import justfatlard.pandorical.protocol.SyncAssetsConfigS2C;
import justfatlard.pandorical.protocol.SyncAssetsS2C;
import justfatlard.pandorical.protocol.StatePropertySpec;
import justfatlard.pandorical.protocol.SyncContentS2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import justfatlard.pandorical.api.VanillaItemOverride;
import justfatlard.pandorical.rail.RailCollision;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ContentRegistry implements ContentApi {
    private final Map<String, RegisteredBlock> blocks = new LinkedHashMap<>();
    private final Map<String, RegisteredItem> items = new LinkedHashMap<>();
    private final Map<String, byte[]> assets = new ConcurrentHashMap<>();

    private final Map<String, String> vanillaAssetOwners = new ConcurrentHashMap<>();
    private volatile List<SyncAssetsS2C> cachedAssetChunks = null;

    private final Map<String, VanillaItemOverride> vanillaItemOverrides = new LinkedHashMap<>();

    /** Kept out of Fabric's registry sync, so a vanilla client can connect. */
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
        cachedAssetChunks = null;
    }

    private boolean solidRails;

    @Override
    public void solidRails() {
        solidRails = true;
        RailCollision.setSolid(true);
        Pandorical.LOGGER.info("Rails are solid for players");
    }

    public boolean railsSolid() {
        return solidRails;
    }

    public void registerServerOnlyNamespace(String namespace) {
        if (!namespace.equals("minecraft")) {
            serverOnlyNamespaces.add(namespace);
            Pandorical.LOGGER.info("Registered server-only namespace: {}", namespace);
        }
    }

    public static boolean isServerOnlyNamespace(String namespace) {
        return serverOnlyNamespaces.contains(namespace);
    }

    public static boolean hasServerOnlyNamespaces() {
        return !serverOnlyNamespaces.isEmpty();
    }

    // A view of a concurrent set, so caching it is safe.
    private static volatile Set<String> cachedUnmodifiableView = null;

    public static Set<String> getServerOnlyNamespaces() {
        Set<String> cached = cachedUnmodifiableView;
        if (cached == null) {
            cached = Collections.unmodifiableSet(serverOnlyNamespaces);
            cachedUnmodifiableView = cached;
        }
        return cached;
    }

    @Override
    public void registerModAssets(String modId) {
        if (!modId.equals("minecraft")) {
            serverOnlyNamespaces.add(modId);
            Pandorical.LOGGER.debug("Tracking server-only namespace from mod assets: {}", modId);
        }

        try {
            var modContainer = FabricLoader.getInstance()
                .getModContainer(modId);
            if (modContainer.isEmpty()) {
                Pandorical.LOGGER.warn("Mod '{}' not found — cannot register assets", modId);
                return;
            }

            var rootPaths = modContainer.get().getRootPaths();
            for (var root : rootPaths) {
                // Also assets/minecraft/: armour layers and villager profession skins resolve to
                // vanilla-namespace paths.
                for (String namespace : new String[] {modId, "minecraft"}) {
                    if (namespace.equals("minecraft") && modId.equals("minecraft")) continue;

                    var assetsDir = root.resolve("assets").resolve(namespace);
                    if (!Files.exists(assetsDir)) continue;

                    try (var walk = Files.walk(assetsDir)) {
                        walk.filter(Files::isRegularFile).forEach(file -> {
                            try {
                                String relativePath = "assets/" + namespace + "/"
                                    + assetsDir.relativize(file).toString();
                                byte[] data = Files.readAllBytes(file);

                                // Warn only on another mod's claim: this reruns on every
                                // registration, so a mod meets its own files constantly.
                                if (namespace.equals("minecraft")) {
                                    String previous = vanillaAssetOwners.put(relativePath, modId);
                                    if (previous != null && !previous.equals(modId)) {
                                        Pandorical.LOGGER.warn(
                                            "[pandorical] '{}' overwrites a vanilla asset '{}'"
                                            + " already registered: {}", modId, previous, relativePath);
                                    }
                                }
                                registerAsset(relativePath, data);
                            } catch (IOException e) {
                                Pandorical.LOGGER.warn("Failed to read asset file {}: {}", file, e.getMessage());
                            }
                        });
                    }
                }
            }

            Pandorical.LOGGER.info("Registered assets for mod '{}' ({} total assets now)", modId, assets.size());
        } catch (Exception e) {
            Pandorical.LOGGER.warn("Failed to scan assets for mod {}: {}", modId, e.getMessage());
        }
    }

    @Override
    public void overrideVanillaItem(String vanillaItemId, VanillaItemOverride override) {
        if (vanillaItemId == null || !vanillaItemId.contains(":")) {
            Pandorical.LOGGER.warn("Invalid vanilla item ID (must be namespace:path): {}", vanillaItemId);
            return;
        }
        vanillaItemOverrides.put(vanillaItemId, override);
        applyVanillaItemOverrideAssets(vanillaItemId, override);
        Pandorical.LOGGER.info("Registered vanilla item override: {}", vanillaItemId);
    }

    /**
     * Lang and textures go in the pandorical namespace: assets/minecraft/lang/en_us.json could be
     * shadowed by the vanilla pack. Only the items/ redirect must live in the item's namespace.
     */
    private void applyVanillaItemOverrideAssets(String vanillaItemId, VanillaItemOverride override) {
        String[] parts = vanillaItemId.split(":", 2);
        String namespace = parts[0];
        String itemName = parts[1];
        String flatKey = namespace + "_" + itemName.replace('/', '_');

        if (override.hasTexture()) {
            registerAsset("assets/pandorical/textures/item/" + flatKey + ".png",
                override.getTextureData());

            String autoModel = "{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": {\n    \"layer0\": \""
                + escapeJson("pandorical:item/" + flatKey) + "\"\n  }\n}\n";
            registerAsset("assets/pandorical/models/item/" + flatKey + ".json",
                autoModel.getBytes(StandardCharsets.UTF_8));

            if (!override.hasModel()) {
                String itemsJson = "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n    \"model\": \""
                    + escapeJson("pandorical:item/" + flatKey) + "\"\n  }\n}\n";
                registerAsset("assets/" + namespace + "/items/" + itemName + ".json",
                    itemsJson.getBytes(StandardCharsets.UTF_8));
            }
        }

        if (override.hasModel()) {
            String json = "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n    \"model\": \""
                + escapeJson(override.getModelPath()) + "\"\n  }\n}\n";
            registerAsset("assets/" + namespace + "/items/" + itemName + ".json",
                json.getBytes(StandardCharsets.UTF_8));
        }

        if (override.hasName()) {
            rebuildVanillaLangFile();
        }
    }

    // registerAsset on one path overwrites, so every lang contributor goes through
    // rebuildVanillaLangFile.
    private final Map<String, String> extraLangEntries = new LinkedHashMap<>();

    public void addLangEntries(Map<String, String> entries) {
        extraLangEntries.putAll(entries);
        rebuildVanillaLangFile();
    }

    private void rebuildVanillaLangFile() {
        Map<String, String> entries = new LinkedHashMap<>();
        for (var e : vanillaItemOverrides.entrySet()) {
            if (!e.getValue().hasName()) continue;
            String[] parts = e.getKey().split(":", 2);
            String langKey = "item." + parts[0] + "." + parts[1].replace('/', '.');
            entries.put(langKey, e.getValue().getDisplayName());
        }
        entries.putAll(extraLangEntries);
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
            sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public boolean hasContent() {
        if (!blocks.isEmpty() || !items.isEmpty()) return true;
        if (!vanillaItemOverrides.isEmpty()) return true;
        if (!assets.isEmpty()) return true;
        return hasServerOnlyNamespaces();
    }

    /** The play-phase fallback of PandoricalSyncTask; sends nothing without blocks or items. */
    public void syncContentTo(ServerPlayer player) {
        List<SyncContentS2C.BlockEntry> blockEntries = buildBlockEntries();
        List<SyncContentS2C.ItemEntry> itemEntries = buildItemEntries();

        if (blockEntries.isEmpty() && itemEntries.isEmpty()) return;

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
            buildPoiTypeEntries(), buildMenuTypeEntries(), buildRecipeBookCategoryEntries(), solidRails));

        if (assetChunkCount > 0) {
            sendAssets(player);
        }
    }

    private static final int CHUNK_SIZE = 900_000;

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
        // Wire format: [pathUTF][dataLen][data] repeated, then gzipped and chunked
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        for (var entry : assets.entrySet()) {
            dos.writeUTF(entry.getKey());
            dos.writeInt(entry.getValue().length);
            dos.write(entry.getValue());
        }
        dos.flush();

        byte[] raw = baos.toByteArray();

        ByteArrayOutputStream gzipBaos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(gzipBaos)) {
            gzos.write(raw);
        }
        byte[] compressed = gzipBaos.toByteArray();

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

    private volatile boolean reportedInferredBlocks = false;
    private volatile boolean reportedUnsyncedNamespaces = false;

    private void reportUnsyncedNamespaces() {
        if (reportedUnsyncedNamespaces) return;
        reportedUnsyncedNamespaces = true;

        Map<String, Integer> counts = new LinkedHashMap<>();
        countUntracked(BuiltInRegistries.BLOCK, counts);
        countUntracked(BuiltInRegistries.ITEM, counts);
        if (counts.isEmpty()) return;

        boolean dedicated = FabricLoader.getInstance()
            .getEnvironmentType() == EnvType.SERVER;
        Pandorical.LOGGER.warn(
            "{} namespace(s) own registry entries but are not registered with Pandorical: {}. "
                + "Their blocks/items sync to Pandorical clients only if the client has that mod installed too. "
                + "{}",
            counts.size(), counts,
            dedicated
                ? "Unexpected on a dedicated server, where every loaded mod is auto-registered."
                : "This host does not auto-register namespaces, so a server-only mod here must call "
                    + "PandoricalApi.content().registerServerOnlyNamespace(...) or registerBlock/registerItem.");
    }

    private static void countUntracked(Registry<?> registry, Map<String, Integer> counts) {
        for (var entry : registry.entrySet()) {
            String namespace = entry.getKey().identifier().getNamespace();
            if (namespace.equals("minecraft") || isServerOnlyNamespace(namespace)) continue;
            if (FabricLoader.getInstance().getModContainer(namespace).isEmpty()) continue;
            counts.merge(namespace, 1, Integer::sum);
        }
    }

    /** Over the payload limit the play-phase content sync fails to send, so its size is logged. */
    private static boolean loggedContentScale = false;

    private static void logContentScale(List<SyncContentS2C.BlockEntry> entries) {
        if (loggedContentScale) return;
        loggedContentScale = true;

        int states = 0;
        int approximateBytes = 0;
        SyncContentS2C.BlockEntry largest = null;

        for (SyncContentS2C.BlockEntry entry : entries) {
            states += entry.stateIds().size();
            // A state id is a var-int of about three bytes at this suite's size.
            approximateBytes += entry.stateIds().size() * 3 + entry.id().length() + 64;
            for (String property : entry.stateProperties()) approximateBytes += property.length();

            if (largest == null || entry.stateIds().size() > largest.stateIds().size()) largest = entry;
        }

        Pandorical.LOGGER.info("Content sync: {} blocks, {} block states, ~{} KB of the 1024 KB packet{}",
            entries.size(), states, approximateBytes / 1024,
            largest == null ? "" : " (largest: " + largest.id() + " at " + largest.stateIds().size() + " states)");

        if (approximateBytes > 700_000) {
            Pandorical.LOGGER.warn("Content sync is nearing the payload limit; past it nothing syncs at all.");
        }
    }

    public List<SyncContentS2C.BlockEntry> buildBlockEntries() {
        List<SyncContentS2C.BlockEntry> blockEntries = new ArrayList<>();
        List<String> inferred = new ArrayList<>();
        for (var entry : BuiltInRegistries.BLOCK.entrySet()) {
            String namespace = entry.getKey().identifier().getNamespace();
            String id = entry.getKey().identifier().toString();

            // A mod can also claim a vanilla block by id, to make it interactive; the client keeps
            // its own block and takes only the flags.
            if (!isServerOnlyNamespace(namespace) && !blocks.containsKey(id)) continue;

            var block = entry.getValue();
            List<Integer> stateIds = new ArrayList<>();
            List<String> stateProps = new ArrayList<>();
            for (var prop : block.getStateDefinition().getProperties()) {
                stateProps.add(StatePropertySpec.encode(prop));
            }
            for (var state : block.getStateDefinition().getPossibleStates()) {
                stateIds.add(Block.BLOCK_STATE_REGISTRY.getId(state));
            }
            String baseBlockId = "";
            String modelId = "";
            boolean interactive = false;
            float destroyTime = BlockRegistration.INHERIT;
            int requiresCorrectTool = BlockRegistration.INHERIT_FLAG;
            var registered = blocks.get(id);
            if (registered != null) {
                baseBlockId = registered.registration().getBaseBlockId();
                modelId = registered.registration().getModelId();
                interactive = registered.registration().isInteractive();
                destroyTime = registered.registration().getDestroyTime();
                requiresCorrectTool = registered.registration().getRequiresCorrectTool();
            }

            // Unset mining values come from the real block, which the server times the dig by;
            // the client predicts from what it is sent.
            var real = block.defaultBlockState();
            if (destroyTime == BlockRegistration.INHERIT) {
                // An unbreakable block's -1 reads as "inherit" on the wire.
                destroyTime = real.getDestroySpeed(EmptyBlockGetter.INSTANCE,
                    BlockPos.ZERO);
            }
            if (requiresCorrectTool == BlockRegistration.INHERIT_FLAG) {
                requiresCorrectTool = real.requiresCorrectToolForDrops() ? 1 : 0;
            }

            // An unregistered block's base is inferred from its sound and may not share its state
            // properties, so these are named in a warning.
            if (baseBlockId.isEmpty()) {
                baseBlockId = inferBaseBlockId(block);
                inferred.add(id);
            }

            byte[] shapeData = serializeBlockShapes(block);
            byte[] lightData = serializeBlockLight(block);

            // No vanilla climbable varies climbability by state.
            boolean climbable = block.defaultBlockState().is(BlockTags.CLIMBABLE);

            blockEntries.add(new SyncContentS2C.BlockEntry(
                id, baseBlockId, stateProps, modelId, stateIds, shapeData, lightData, climbable, interactive,
                destroyTime, requiresCorrectTool));
        }

        reportUnsyncedNamespaces();

        if (!inferred.isEmpty() && !reportedInferredBlocks) {
            reportedInferredBlocks = true;
            int shown = Math.min(inferred.size(), 12);
            Pandorical.LOGGER.warn(
                "{} synced block(s) were never registered through PandoricalApi.content().registerBlock(...); "
                    + "their client-side base block is inferred from sound alone: {}{}",
                inferred.size(), inferred.subList(0, shown),
                inferred.size() > shown ? " (+" + (inferred.size() - shown) + " more)" : "");
        }

        logContentScale(blockEntries);
        return blockEntries;
    }

    private static byte[] serializeBlockLight(Block block) {
        var states = block.getStateDefinition().getPossibleStates();
        byte[] light = new byte[states.size()];
        for (int i = 0; i < light.length; i++) {
            light[i] = (byte) states.get(i).getLightEmission();
        }
        return light;
    }

    /**
     * Per state: [outlineBoxCount:byte][boxes][collisionBoxCount:byte][boxes], each box six floats,
     * min x, y, z then max x, y, z.
     */
    private static byte[] serializeBlockShapes(Block block) {
        try {
            var baos = new ByteArrayOutputStream();
            var dos = new DataOutputStream(baos);
            var emptyGetter = EmptyBlockGetter.INSTANCE;
            var origin = BlockPos.ZERO;
            var ctx = CollisionContext.empty();

            for (var state : block.getStateDefinition().getPossibleStates()) {
                var outline = state.getShape(emptyGetter, origin, ctx);
                writeShape(dos, outline);
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

    private static void writeShape(DataOutputStream dos, VoxelShape shape) throws IOException {
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

    public List<SyncContentS2C.ItemEntry> buildItemEntries() {
        List<SyncContentS2C.ItemEntry> itemEntries = new ArrayList<>();
        for (var entry : BuiltInRegistries.ITEM.entrySet()) {
            String namespace = entry.getKey().identifier().getNamespace();
            if (!isServerOnlyNamespace(namespace)) continue;

            var item = entry.getValue();
            String id = entry.getKey().identifier().toString();
            var registered = items.get(id);
            String modelId = registered != null ? registered.registration().getModelId() : "";
            boolean glint = registered != null && registered.registration().hasGlint();

            int maxStack = item.getDefaultMaxStackSize();
            Integer maxDamageObj = item.components().get(DataComponents.MAX_DAMAGE);
            int maxDamage = maxDamageObj != null ? maxDamageObj : 0;

            String equipSlot = "";
            var equippable = item.components().get(DataComponents.EQUIPPABLE);
            if (equippable != null) {
                equipSlot = equippable.slot().getName();
                // Without the asset id the client has no armour model and draws the item's sprite.
                var asset = equippable.assetId();
                if (asset.isPresent()) {
                    equipSlot = equipSlot + "|" + asset.get().identifier();
                }
            }

            String declaredTool = registered != null ? registered.registration().getToolSpec() : "";
            String toolType = declaredTool.isEmpty() ? inferToolType(item) : declaredTool;

            itemEntries.add(new SyncContentS2C.ItemEntry(
                id, modelId, maxStack, maxDamage, glint, equipSlot, toolType, foodSpec(item)));
        }
        return itemEntries;
    }

    /** "" for anything inedible. Without it the client's stand-in has no food component. */
    private static String foodSpec(Item item) {
        var food = item.components().get(DataComponents.FOOD);
        if (food == null) return "";

        var consumable = item.components().get(DataComponents.CONSUMABLE);
        float seconds = consumable != null ? consumable.consumeSeconds() : 1.6F;

        return String.join("|", String.valueOf(food.nutrition()),
            String.valueOf(food.saturation()), String.valueOf(food.canAlwaysEat()),
            String.valueOf(seconds));
    }

    private static String inferToolType(Item item) {
        var tool = item.components().get(DataComponents.TOOL);
        if (tool != null) return "tool";
        return "";
    }

    public void autoScanAllModAssets() {
        for (String namespace : serverOnlyNamespaces) {
            boolean hasAssets = assets.keySet().stream().anyMatch(k -> k.startsWith("assets/" + namespace + "/"));
            if (hasAssets) continue;

            var modContainer = FabricLoader.getInstance().getModContainer(namespace);
            if (modContainer.isEmpty()) continue;

            registerModAssets(namespace);
        }
    }

    public List<SyncAssetsConfigS2C> buildConfigAssetChunks() throws IOException {
        autoScanAllModAssets();
        if (assets.isEmpty()) return List.of();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        for (var entry : assets.entrySet()) {
            dos.writeUTF(entry.getKey());
            dos.writeInt(entry.getValue().length);
            dos.write(entry.getValue());
        }
        dos.flush();

        byte[] raw = baos.toByteArray();

        ByteArrayOutputStream gzipBaos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(gzipBaos)) {
            gzos.write(raw);
        }
        byte[] compressed = gzipBaos.toByteArray();

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

    private List<String> scanRegistry(Registry<?> registry) {
        List<String> result = new ArrayList<>();
        for (var entry : registry.entrySet()) {
            String namespace = entry.getKey().identifier().getNamespace();
            if (!isServerOnlyNamespace(namespace)) continue;
            result.add(entry.getKey().identifier().toString());
        }
        return result;
    }

    public List<String> buildEntityTypeEntries() {
        return scanRegistry(BuiltInRegistries.ENTITY_TYPE);
    }

    public List<String> buildBlockEntityTypeEntries() {
        return scanRegistry(BuiltInRegistries.BLOCK_ENTITY_TYPE);
    }

    public List<String> buildVillagerProfessionEntries() {
        return scanRegistry(BuiltInRegistries.VILLAGER_PROFESSION);
    }

    public List<String> buildPoiTypeEntries() {
        return scanRegistry(BuiltInRegistries.POINT_OF_INTEREST_TYPE);
    }

    public List<String> buildMenuTypeEntries() {
        return scanRegistry(BuiltInRegistries.MENU);
    }

    public List<String> buildRecipeBookCategoryEntries() {
        return scanRegistry(BuiltInRegistries.RECIPE_BOOK_CATEGORY);
    }

    private static String inferBaseBlockId(Block block) {
        // The client works out the block's type from its state properties; the base gives sounds.
        var sound = block.defaultBlockState().getSoundType();
        return inferBaseBlockFromSound(sound);
    }

    private static String inferBaseBlockFromSound(SoundType sound) {
        if (sound == SoundType.GRASS)   return "minecraft:grass_block";
        if (sound == SoundType.GRAVEL)  return "minecraft:gravel";
        if (sound == SoundType.WOOD)    return "minecraft:oak_planks";
        if (sound == SoundType.STONE)   return "minecraft:stone";
        if (sound == SoundType.METAL)   return "minecraft:iron_block";
        if (sound == SoundType.GLASS)   return "minecraft:glass";
        if (sound == SoundType.SAND)    return "minecraft:sand";
        if (sound == SoundType.WOOL)    return "minecraft:white_wool";
        if (sound == SoundType.SNOW)    return "minecraft:snow_block";
        if (sound == SoundType.COPPER)  return "minecraft:copper_block";
        if (sound == SoundType.CORAL_BLOCK)     return "minecraft:brain_coral_block";
        if (sound == SoundType.NETHER_BRICKS)   return "minecraft:nether_bricks";
        if (sound == SoundType.NYLIUM)          return "minecraft:crimson_nylium";
        if (sound == SoundType.NETHERRACK)      return "minecraft:netherrack";
        if (sound == SoundType.SOUL_SAND)       return "minecraft:soul_sand";
        if (sound == SoundType.SOUL_SOIL)       return "minecraft:soul_soil";
        if (sound == SoundType.BASALT)          return "minecraft:basalt";
        if (sound == SoundType.MOSS)            return "minecraft:moss_block";
        if (sound == SoundType.MUD)             return "minecraft:mud";
        if (sound == SoundType.MUDDY_MANGROVE_ROOTS) return "minecraft:muddy_mangrove_roots";
        if (sound == SoundType.ROOTED_DIRT)     return "minecraft:rooted_dirt";
        if (sound == SoundType.PACKED_MUD)      return "minecraft:packed_mud";
        if (sound == SoundType.DEEPSLATE)       return "minecraft:deepslate";
        if (sound == SoundType.CALCITE)         return "minecraft:calcite";
        if (sound == SoundType.TUFF)            return "minecraft:tuff";
        if (sound == SoundType.DRIPSTONE_BLOCK) return "minecraft:dripstone_block";
        if (sound == SoundType.AMETHYST)        return "minecraft:amethyst_block";
        return "minecraft:stone";
    }

    public Map<String, RegisteredBlock> getBlocks() { return blocks; }
    public Map<String, RegisteredItem> getItems() { return items; }
}
