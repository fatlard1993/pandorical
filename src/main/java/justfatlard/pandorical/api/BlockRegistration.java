package justfatlard.pandorical.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder for custom block registration.
 */
public class BlockRegistration {
    private String baseBlockId = "minecraft:stone";
    private final List<String> stateProperties = new ArrayList<>();
    private String modelId = "";

    /**
     * Base block to clone properties from (strength, sound, etc).
     */
    public BlockRegistration baseBlock(String baseBlockId) {
        this.baseBlockId = baseBlockId;
        return this;
    }

    /**
     * Add a block state property (e.g., "horizontal_facing", "waterlogged").
     */
    public BlockRegistration property(String propertyName) {
        this.stateProperties.add(propertyName);
        return this;
    }

    /**
     * Model resource location (e.g., "big-boats:block/helm").
     */
    public BlockRegistration model(String modelId) {
        this.modelId = modelId;
        return this;
    }

    public String getBaseBlockId() { return baseBlockId; }
    public List<String> getStateProperties() { return stateProperties; }
    public String getModelId() { return modelId; }
}
