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

    public String getBaseBlockId() { return baseBlockId; }
    public List<String> getStateProperties() { return stateProperties; }
    public String getModelId() { return modelId; }
}
