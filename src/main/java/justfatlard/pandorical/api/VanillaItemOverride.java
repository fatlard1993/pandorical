package justfatlard.pandorical.api;

import java.io.IOException;
import java.nio.file.Files;

/**
 * Builder for overriding a vanilla item's appearance for Pandorical clients.
 *
 * The overrides are injected into the VirtualResourcePack at TOP priority,
 * so they take effect over vanilla resources. Vanilla clients are unaffected.
 *
 * Usage:
 * <pre>
 *   PandoricalApi.content().overrideVanillaItem("minecraft:rabbit_hide",
 *       new VanillaItemOverride()
 *           .name("Leather Scraps")
 *           .textureFrom("my-mod", "textures/item/leather_scraps.png"));
 * </pre>
 */
public class VanillaItemOverride {
    private String displayName = null;
    private byte[] textureData = null;
    private String modelPath = null;

    /**
     * Override the item's display name for Pandorical clients.
     * Writes into assets/{namespace}/lang/en_us.json on the client.
     */
    public VanillaItemOverride name(String displayName) {
        this.displayName = displayName;
        return this;
    }

    /**
     * Override the item's texture with raw PNG bytes.
     * Replaces assets/{namespace}/textures/item/{name}.png on the client.
     * Works for flat 2D items without needing a model override.
     */
    public VanillaItemOverride texture(byte[] data) {
        this.textureData = data;
        return this;
    }

    /**
     * Load the texture from a mod's classpath assets.
     * Path is relative to assets/{modId}/, e.g. "textures/item/leather_scraps.png".
     */
    public VanillaItemOverride textureFrom(String modId, String assetPath) {
        try {
            var modContainer = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(modId);
            if (modContainer.isEmpty()) {
                throw new IllegalArgumentException("Mod not found: " + modId);
            }
            for (var root : modContainer.get().getRootPaths()) {
                var file = root.resolve("assets").resolve(modId).resolve(assetPath);
                if (Files.exists(file)) {
                    this.textureData = Files.readAllBytes(file);
                    return this;
                }
            }
            throw new IllegalArgumentException("Asset not found in mod " + modId + ": " + assetPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load texture from mod " + modId + ": " + assetPath, e);
        }
    }

    /**
     * Override the item model by pointing assets/{namespace}/items/{name}.json at a
     * different model resource location. Use this when you want a model from your mod
     * namespace, or when the vanilla model chain isn't sufficient for the retexture.
     *
     * If only a texture is provided (no model override), the vanilla items/ JSON and
     * model chain are preserved — only the texture PNG is replaced.
     */
    public VanillaItemOverride model(String modelPath) {
        this.modelPath = modelPath;
        return this;
    }

    public boolean hasName()    { return displayName != null; }
    public boolean hasTexture() { return textureData != null; }
    public boolean hasModel()   { return modelPath != null; }

    public String getDisplayName() { return displayName; }
    public byte[] getTextureData() { return textureData; }
    public String getModelPath()   { return modelPath; }
}
