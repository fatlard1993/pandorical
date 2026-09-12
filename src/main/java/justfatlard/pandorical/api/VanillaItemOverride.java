package justfatlard.pandorical.api;

import java.io.IOException;
import java.nio.file.Files;
import net.fabricmc.loader.api.FabricLoader;

public class VanillaItemOverride {
    private String displayName = null;
    private byte[] textureData = null;
    private String modelPath = null;

    /** Written into the client's {@code assets/<namespace>/lang/en_us.json}. */
    public VanillaItemOverride name(String displayName) {
        this.displayName = displayName;
        return this;
    }

    /**
     * PNG bytes replacing {@code assets/<namespace>/textures/item/<name>.png}; enough for a flat
     * item without a model override.
     */
    public VanillaItemOverride texture(byte[] data) {
        this.textureData = data;
        return this;
    }

    /** @param assetPath relative to {@code assets/<modId>/}, e.g. {@code "textures/item/leather_scraps.png"} */
    public VanillaItemOverride textureFrom(String modId, String assetPath) {
        try {
            var modContainer = FabricLoader.getInstance().getModContainer(modId);
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
     * Point {@code assets/<namespace>/items/<name>.json} at another model. Without it the vanilla
     * model chain is kept and only the texture PNG is replaced.
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
