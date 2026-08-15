package justfatlard.pandorical.api;

import net.minecraft.server.level.ServerPlayer;

/**
 * API for server mods to register custom blocks and items.
 * Content is synced to Pandorical clients on join.
 */
public interface ContentApi {
    /**
     * Declare a custom block for client sync. Call during onInitialize.
     * Stores metadata sent to Pandorical clients so they can register the block
     * locally; the server-side block must still be registered separately with
     * vanilla's Registry.register().
     */
    void registerBlock(String id, BlockRegistration registration);

    /** Same contract as {@link #registerBlock}, for items. */
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
     * Override a vanilla item's appearance (name, texture, model) for Pandorical
     * clients. Injected into the VirtualResourcePack at TOP priority, so it wins
     * over vanilla resources; vanilla clients are unaffected. Multiple name
     * overrides targeting the same namespace merge into a single lang file.
     *
     * @param vanillaItemId full item ID, e.g. "minecraft:rabbit_hide"
     */
    void overrideVanillaItem(String vanillaItemId, VanillaItemOverride override);
}
