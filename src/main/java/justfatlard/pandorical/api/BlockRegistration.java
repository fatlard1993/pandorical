package justfatlard.pandorical.api;

import java.util.ArrayList;
import java.util.List;

public class BlockRegistration {
    private String baseBlockId = "minecraft:stone";
    private final List<String> stateProperties = new ArrayList<>();
    private String modelId = "";
    private boolean interactive = false;
    private float destroyTime = INHERIT;
    private int requiresCorrectTool = INHERIT_FLAG;

    /** Destroy time meaning "whatever the base block says". */
    public static final float INHERIT = -1.0F;

    /** Tool flag meaning "whatever the base block says". */
    public static final int INHERIT_FLAG = -1;

    /**
     * The client builds its stand-in from this block's settings and class, so pick it for
     * material feel (sound, hardness, slab or stair). State properties always come from the real
     * server block; a base whose same-named property has a different value range is rejected.
     */
    public BlockRegistration baseBlock(String baseBlockId) {
        this.baseBlockId = baseBlockId;
        return this;
    }

    /** Not acted on: the client takes state properties from the real server block. */
    public BlockRegistration property(String propertyName) {
        this.stateProperties.add(propertyName);
        return this;
    }

    /**
     * Declare that right-clicking this block does something. Without it the client's stand-in
     * treats the click as unhandled and predicts placing the held item, which the server never
     * does, leaving a phantom block and a wrong stack count until a resync.
     */
    public BlockRegistration interactive() {
        this.interactive = true;
        return this;
    }

    public boolean isInteractive() { return interactive; }

    /** Not acted on by the client: appearance comes from the blockstate and model assets. */
    public BlockRegistration model(String modelId) {
        this.modelId = modelId;
        return this;
    }

    /**
     * Breaking time, when the base block's is wrong. The client predicts digging against the
     * stand-in, so set it whenever the server block's hardness differs from the base's. Blast
     * resistance is settled on the server alone.
     *
     * @param destroyTime hardness, as {@code Properties#strength} takes it
     */
    public BlockRegistration strength(float destroyTime) {
        this.destroyTime = destroyTime;
        return this;
    }

    /**
     * Whether the client applies the wrong-tool penalty, which multiplies its predicted dig time
     * about fivefold. A block that decides its drops per part wants {@code false}, whatever its
     * base says.
     */
    public BlockRegistration requiresCorrectTool(boolean required) {
        this.requiresCorrectTool = required ? 1 : 0;
        return this;
    }

    public float getDestroyTime() { return destroyTime; }
    public int getRequiresCorrectTool() { return requiresCorrectTool; }

    public String getBaseBlockId() { return baseBlockId; }
    public List<String> getStateProperties() { return stateProperties; }
    public String getModelId() { return modelId; }
}
