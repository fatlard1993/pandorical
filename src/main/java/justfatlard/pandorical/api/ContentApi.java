package justfatlard.pandorical.api;

import net.minecraft.server.level.ServerPlayer;

/** Custom blocks, items and assets, synced to Pandorical clients on join. */
public interface ContentApi {
    /**
     * Declare a block for client sync, during onInitialize. The server-side block must still be
     * registered with vanilla's {@code Registry.register()}.
     */
    void registerBlock(String id, BlockRegistration registration);

    /** Same contract as {@link #registerBlock}, for items. */
    void registerItem(String id, ItemRegistration registration);

    /** @param path relative to {@code assets/}, e.g. {@code "big-boats/models/block/helm.json"} */
    void registerAsset(String path, byte[] data);

    /** Register every file under the mod's {@code assets/<modId>/} on the classpath. */
    void registerModAssets(String modId);

    /**
     * Track a namespace's registry entries for sync. Done for you by registerBlock, registerItem
     * and registerModAssets; call it for a mod that registers straight into Minecraft's
     * registries.
     */
    void registerServerOnlyNamespace(String namespace);

    /**
     * Every rail holds players up: two pixels of deck on a flat one, steps on a slope. Players
     * only; carts and mobs are unaffected. Synced to clients with the rest of the content.
     */
    void solidRails();

    /**
     * Override a vanilla item's name, texture or model for Pandorical clients. Served above
     * vanilla's resources; vanilla clients are unaffected. Name overrides in one namespace merge
     * into a single lang file.
     *
     * @param vanillaItemId full item ID, e.g. "minecraft:rabbit_hide"
     */
    void overrideVanillaItem(String vanillaItemId, VanillaItemOverride override);
}
