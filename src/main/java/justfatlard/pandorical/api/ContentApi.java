package justfatlard.pandorical.api;

import net.minecraft.server.level.ServerPlayer;

/**
 * API for server mods to register custom blocks and items.
 * Content is synced to Pandorical clients on join.
 */
public interface ContentApi {
    /**
     * Declare a custom block for client sync. Call during onInitialize.
     * This stores metadata that will be sent to Pandorical clients so they can
     * register the block in their local registries. The server-side block must be
     * registered separately with vanilla's Registry.register().
     */
    void registerBlock(String id, BlockRegistration registration);

    /**
     * Declare a custom item for client sync. Call during onInitialize.
     * This stores metadata that will be sent to Pandorical clients so they can
     * register the item in their local registries. The server-side item must be
     * registered separately with vanilla's Registry.register().
     */
    void registerItem(String id, ItemRegistration registration);

    /**
     * Register asset data (model JSON, texture PNG) to be synced to clients.
     * Path is relative to assets/ (e.g., "big-boats/models/block/helm.json").
     */
    void registerAsset(String path, byte[] data);

    /**
     * Register all assets from a mod's resources directory.
     * Scans the classpath for assets/{namespace}/ and registers all found files.
     */
    void registerModAssets(String modId);

    /**
     * Register a mod namespace for content tracking.
     * Called automatically when using registerBlock/registerItem/registerModAssets.
     * Can be called explicitly for mods that register entries directly with
     * Minecraft's registries without using Pandorical's block/item registration.
     */
    void registerServerOnlyNamespace(String namespace);

    /**
     * Override a vanilla item's appearance for Pandorical clients.
     * Supports renaming, retexturing, and model replacement.
     *
     * The override is injected into the VirtualResourcePack at TOP priority,
     * so it takes effect over vanilla resources. Vanilla clients are unaffected.
     *
     * Can be called multiple times for different items. Multiple name overrides
     * targeting the same namespace are merged into a single lang file.
     *
     * @param vanillaItemId Full item ID, e.g. "minecraft:rabbit_hide"
     * @param override      The override to apply
     */
    void overrideVanillaItem(String vanillaItemId, VanillaItemOverride override);
}
