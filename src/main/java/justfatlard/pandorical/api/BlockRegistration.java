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
    private boolean interactive = false;
    private float destroyTime = INHERIT;
    private int requiresCorrectTool = INHERIT_FLAG;

    /** Destroy time meaning "whatever the base block says". */
    public static final float INHERIT = -1.0F;

    /** Tool flag meaning "whatever the base block says". */
    public static final int INHERIT_FLAG = -1;

    /**
     * Base block to clone properties from (strength, sound, etc).
     *
     * <p>This one is load-bearing: the client builds its stand-in from this block's
     * settings and block class. Pick it for material feel (sound, hardness, whether
     * it is a slab/stair/etc), not for its state properties: those always come from
     * the real server block over the wire, and a base whose same-named property has a
     * different value range is rejected rather than followed.
     */
    public BlockRegistration baseBlock(String baseBlockId) {
        this.baseBlockId = baseBlockId;
        return this;
    }

    /**
     * Add a block state property (e.g., "horizontal_facing", "waterlogged").
     *
     * <p><b>Advisory only today.</b> The client rebuilds state properties from the real
     * server block's state definition, which is sent on the wire with exact value
     * ranges, so listing them here changes nothing.
     */
    public BlockRegistration property(String propertyName) {
        this.stateProperties.add(propertyName);
        return this;
    }

    /**
     * Model resource location (e.g., "big-boats:block/helm").
     *
     * <p><b>Advisory only today.</b> Sent on the wire, not acted on by the client:
     * appearance comes from the synced blockstate/model assets in your jar.
     */
    /**
     * Say that right-clicking this block does something, so the client stops guessing.
     *
     * <p>The client's copy of a synced block is a plain stand-in: it has none of the server
     * block's behaviour, so a right-click on it looks unhandled and the client goes ahead and
     * predicts what an unhandled right-click means - placing whatever is in hand. The server
     * opens a screen instead and places nothing, and the player watches a block appear, vanish,
     * and leave a stack count that stays wrong until something forces a resync.
     *
     * <p>Declared rather than worked out from the block, because the only honest way to detect
     * it is to ask whether the class overrides useItemOn, and method names are intermediary at
     * runtime - a lookup by the name written here would find nothing and quietly answer no.
     */
    public BlockRegistration interactive() {
        this.interactive = true;
        return this;
    }

    public boolean isInteractive() { return interactive; }

    public BlockRegistration model(String modelId) {
        this.modelId = modelId;
        return this;
    }

    /**
     * How long this block takes to break, when the base block's own answer is wrong.
     *
     * <p>Worth setting whenever the server block was built up by hand rather than copied from the
     * base: breaking is predicted on the client, against the stand-in, while everything the server
     * decides is measured against the real block. Leave them disagreeing and the dig takes a
     * different length of time than the player's screen is drawing, and any progress bar rendered
     * from the server's side disagrees with the swing that is producing it.
     *
     * <p>Only the mining half of {@code strength} is here. Blast resistance is settled entirely on
     * the server, so the stand-in has no use for it.
     *
     * @param destroyTime hardness, as {@code Properties#strength} takes it
     */
    public BlockRegistration strength(float destroyTime) {
        this.destroyTime = destroyTime;
        return this;
    }

    /**
     * Whether the client should apply the wrong-tool penalty to this block.
     *
     * <p>The larger of the two mining mismatches, and the easier one to acquire by accident: a
     * stand-in that wants a pickaxe predicts roughly five times the dig that a server which does
     * not care will actually perform. A block that decides its drops per-part rather than as a
     * whole wants {@code false} here, and usually has a base block that says otherwise.
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
