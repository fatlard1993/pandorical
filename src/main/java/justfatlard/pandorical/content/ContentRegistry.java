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

/**
 * Server-side content registry. Stores block/item registrations and asset data.
 * Syncs to Pandorical clients on join.
 */
public class ContentRegistry implements ContentApi {
    private final Map<String, RegisteredBlock> blocks = new LinkedHashMap<>();
    private final Map<String, RegisteredItem> items = new LinkedHashMap<>();
    private final Map<String, byte[]> assets = new ConcurrentHashMap<>();

    /** Who claimed each vanilla-namespace path, so a genuine clash between two mods is audible. */
    private final Map<String, String> vanillaAssetOwners = new ConcurrentHashMap<>();
    private volatile List<SyncAssetsS2C> cachedAssetChunks = null;

    /** Vanilla item overrides: keyed by full item ID, e.g. "minecraft:rabbit_hide" */
    private final Map<String, VanillaItemOverride> vanillaItemOverrides = new LinkedHashMap<>();

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
        cachedAssetChunks = null;
    }

    /**
     * Register an entire mod namespace as server-only for registry sync bypass.
     * Call this during onInitialize for mods that register custom content via Pandorical.
     */
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

    /** Hot-path guard: cheaper than materialising the namespace view. */
    public static boolean hasServerOnlyNamespaces() {
        return !serverOnlyNamespaces.isEmpty();
    }

    // Cached unmodifiable view; safe because ConcurrentHashMap.KeySetView is thread-safe
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

        // Scan the mod's jar for assets/{modId}/ files and register them
        try {
            var modContainer = FabricLoader.getInstance()
                .getModContainer(modId);
            if (modContainer.isEmpty()) {
                Pandorical.LOGGER.warn("Mod '{}' not found — cannot register assets", modId);
                return;
            }

            var rootPaths = modContainer.get().getRootPaths();
            for (var root : rootPaths) {
                // The mod's own namespace, and the vanilla one it may have had to borrow.
                //
                // Some assets are not free to live under a mod's name. An armour layer is looked
                // up from the equipment asset the material names, and a villager profession's
                // skin from a path the game builds itself - both land under assets/minecraft/,
                // and scanning only assets/<modId>/ left them on the server. The item icon
                // arrived and the thing worn on the body did not: a quartz helmet you could hold
                // and could not see, in three mods at once.
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

                                // Two mods writing one vanilla path is a real possibility now
                                // that this namespace is in scope, and the loser would fail
                                // invisibly. Only worth saying when the other one is somebody
                                // else: this runs again every time a mod registers a block or an
                                // item, so a mod meets its own files constantly.
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
     * Generate and register assets for a vanilla item override.
     *
     * <p>Lang and texture overrides live in the pandorical namespace, which the
     * VirtualResourcePack definitely serves (lang files from any namespace can carry
     * keys for any other; writing assets/minecraft/lang/en_us.json risks being shadowed
     * by the built-in vanilla pack). Only the items/ redirect JSON must live in the
     * item's own namespace, a single file far less likely to be shadowed.
     */
    private void applyVanillaItemOverrideAssets(String vanillaItemId, VanillaItemOverride override) {
        String[] parts = vanillaItemId.split(":", 2);
        String namespace = parts[0];
        String itemName = parts[1];
        // Flat key safe for use in file names: "minecraft:rabbit_hide" → "minecraft_rabbit_hide"
        String flatKey = namespace + "_" + itemName.replace('/', '_');

        if (override.hasTexture()) {
            registerAsset("assets/pandorical/textures/item/" + flatKey + ".png",
                override.getTextureData());

            String autoModel = "{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": {\n    \"layer0\": \""
                + escapeJson("pandorical:item/" + flatKey) + "\"\n  }\n}\n";
            registerAsset("assets/pandorical/models/item/" + flatKey + ".json",
                autoModel.getBytes(StandardCharsets.UTF_8));

            // Redirect the vanilla item's definition to the generated model, unless an
            // explicit model override is set (handled below).
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

    // Extra lang entries contributed outside the vanilla-override path, e.g.
    // keybind slot names from KeybindPool. Merged into the same synced
    // pandorical lang file (registerAsset on one path overwrites, so all
    // contributors must go through the single rebuild below).
    private final Map<String, String> extraLangEntries = new LinkedHashMap<>();

    /** Add lang entries to the synced pandorical lang file and rebuild it. */
    public void addLangEntries(Map<String, String> entries) {
        extraLangEntries.putAll(entries);
        rebuildVanillaLangFile();
    }

    /**
     * Rebuild the merged lang file for all vanilla item name overrides and
     * extra contributed entries
     * (namespace rationale in {@link #applyVanillaItemOverrideAssets}).
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

    /**
     * The fallback content sync, sent in the play phase to a client the configuration phase did not
     * make content-ready: the same blocks, items, registry stubs and assets PandoricalSyncTask sends
     * there, in the play-phase payloads. Sends nothing when there are no blocks or items.
     */
    public void syncContentTo(ServerPlayer player) {
        List<SyncContentS2C.BlockEntry> blockEntries = buildBlockEntries();
        List<SyncContentS2C.ItemEntry> itemEntries = buildItemEntries();

        if (blockEntries.isEmpty() && itemEntries.isEmpty()) return;

        // The content packet carries the chunk count so the client knows what to expect
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

    /** Compressed chunks are cached so they aren't rebuilt per player. */
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

    /** Reported once, not per joining player. */
    private volatile boolean reportedInferredBlocks = false;
    private volatile boolean reportedUnsyncedNamespaces = false;

    /**
     * Name any mod that owns block/item registry entries but is not registered with
     * Pandorical at all, on every kind of host.
     *
     * <p>Severity depends on the host, which is exactly why this cannot be left to
     * the dedicated-server path: {@code autoRegisterServerOnlyNamespaces()} only runs
     * on a dedicated server, where an undeclared mod at least gets swept up with a
     * guessed base block. On an integrated or LAN host that sweep never happens, so
     * an undeclared mod syncs nothing whatsoever and its blocks simply do not exist
     * for Pandorical clients. Silent either way without this.
     */
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

    /**
     * One-time note of how much of the content packet the block table is using.
     *
     * <p>Block and item content goes out as a single custom payload, and a custom payload stops at
     * a megabyte. Every state of every registered block puts an id in it, so the packet grows with
     * the suite rather than with any one mod - and a mod registering a block with a large state
     * space (a pair of materials in one block is ten thousand states on its own) spends a chunk of
     * a budget nobody is watching.
     *
     * <p>Silence is the failure mode worth fearing here: over the limit the payload does not
     * truncate, it fails to send, and every Pandorical feature goes missing at once with nothing to
     * connect it back to the mod that tipped it over.
     */
    private static boolean loggedContentScale = false;

    private static void logContentScale(List<SyncContentS2C.BlockEntry> entries) {
        if (loggedContentScale) return;
        loggedContentScale = true;

        int states = 0;
        int approximateBytes = 0;
        SyncContentS2C.BlockEntry largest = null;

        for (SyncContentS2C.BlockEntry entry : entries) {
            states += entry.stateIds().size();
            // A state id is a var-int into the global block state registry - three bytes once a
            // suite this size is registered - and the rest is the id and the property spellings.
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

            // Namespace decides it for the blocks this server invented, but a mod can also claim
            // a vanilla block by naming it outright - which is the only way to say that a
            // right-click on, say, a player head is the server's to answer. The client already
            // handles an id it owns: it finds the block present, keeps its own, and takes only
            // the flags. Nothing is registered twice and no protocol changed to allow it.
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

            // What the registration leaves unsaid is read off the real block rather than left to
            // the stand-in's base. The server times the dig from the real block's hardness and tool
            // requirement, and the client predicts it from whatever it was sent: a cloud standing in
            // as snow dug in half the server's time, so with a shovel the client broke it instantly,
            // the server put it back, and the client broke it again, over and over.
            var real = block.defaultBlockState();
            if (destroyTime == BlockRegistration.INHERIT) {
                // An unbreakable block's -1 reads as "inherit" on the wire, which leaves such a
                // block exactly as it was before this: no worse, and nothing to predict.
                destroyTime = real.getDestroySpeed(EmptyBlockGetter.INSTANCE,
                    BlockPos.ZERO);
            }
            if (requiresCorrectTool == BlockRegistration.INHERIT_FLAG) {
                requiresCorrectTool = real.requiresCorrectToolForDrops() ? 1 : 0;
            }

            // Auto-detected blocks (not registered via PandoricalApi) get an inferred base
            // so the client can still pick the right block class and Properties.
            if (baseBlockId.isEmpty()) {
                baseBlockId = inferBaseBlockId(block);
                // A sound-inferred base can hand the client a block class carrying
                // different state properties than this block has, which the client can
                // only paper over with a fallback state (see ContentManager). Name them
                // here so the mismatch is diagnosable at boot instead of in someone's world.
                inferred.add(id);
            }

            byte[] shapeData = serializeBlockShapes(block);
            byte[] lightData = serializeBlockLight(block);

            // Read off the block's own default state: climbability is a property of the block, and
            // no vanilla climbable varies it by state.
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

    /** One byte of light per state, in the order the block's own definition lists its states. */
    private static byte[] serializeBlockLight(Block block) {
        var states = block.getStateDefinition().getPossibleStates();
        byte[] light = new byte[states.size()];
        for (int i = 0; i < light.length; i++) {
            light[i] = (byte) states.get(i).getLightEmission();
        }
        return light;
    }

    /**
     * Serialize outline and collision VoxelShapes for all states of a block.
     * Format per state: [numOutlineBoxes:byte][boxes...][numCollisionBoxes:byte][boxes...]
     * Each box: [minX:float][minY:float][minZ:float][maxX:float][maxY:float][maxZ:float]
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

    /** Used by both play-phase and config-phase sync. */
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
                // The asset id as well, because the slot alone only says where a thing is worn.
                // Without it the client has nothing to hang an armour model on and falls back to
                // pasting the item's own sprite flat on the wearer: a quartz helmet came out as a
                // white square standing up on the player's head.
                var asset = equippable.assetId();
                if (asset.isPresent()) {
                    equipSlot = equipSlot + "|" + asset.get().identifier();
                }
            }

            // A declared tool carries the material's numbers; the bare "tool" from
            // inferToolType only ever said yes-or-no, which the client could do nothing with.
            String declaredTool = registered != null ? registered.registration().getToolSpec() : "";
            String toolType = declaredTool.isEmpty() ? inferToolType(item) : declaredTool;

            itemEntries.add(new SyncContentS2C.ItemEntry(
                id, modelId, maxStack, maxDamage, glint, equipSlot, toolType, foodSpec(item)));
        }
        return itemEntries;
    }

    /**
     * What the client needs to eat this the way the server does, or "" for anything inedible.
     *
     * <p>Read off the item's own components rather than declared by the mod, for the same reason
     * the equipment slot is: the server item already carries the answer, and a second place to say
     * it is a second place to say it differently.
     *
     * <p>Without this the client's stand-in is a bare item with no food component, so a synced
     * edible has no eating animation, no eating sound and no hunger restored on the client's own
     * reckoning - you hold right-click and nothing whatsoever happens on screen while the server
     * quietly feeds you.
     */
    private static String foodSpec(Item item) {
        var food = item.components().get(DataComponents.FOOD);
        if (food == null) return "";

        var consumable = item.components().get(DataComponents.CONSUMABLE);
        float seconds = consumable != null ? consumable.consumeSeconds() : 1.6F;

        return String.join("|", String.valueOf(food.nutrition()),
            String.valueOf(food.saturation()), String.valueOf(food.canAlwaysEat()),
            String.valueOf(seconds));
    }

    /** Returns "tool" or "": tools are data-driven via the Tool component in MC 26.1+. */
    private static String inferToolType(Item item) {
        var tool = item.components().get(DataComponents.TOOL);
        if (tool != null) return "tool";
        return "";
    }

    /**
     * Auto-scan and register assets for all server-only mods that haven't
     * explicitly registered their assets via PandoricalApi.content().registerModAssets().
     */
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
        // Match by SoundType to get the right break/place/step sounds and material feel;
        // the client detects block type (slab, stair, etc.) from state properties independently.
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
        // CLAY removed in MC 26.1
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
