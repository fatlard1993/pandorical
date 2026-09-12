package justfatlard.pandorical.api;

import net.minecraft.world.item.ToolMaterial;

/**
 * Builder for custom item registration.
 */
public class ItemRegistration {
    private String modelId = "";
    private String toolSpec = "";
    private int maxStackSize = 64;
    private boolean hasGlint = false;

    /**
     * Model resource location (e.g., "big-boats:item/christening_bottle").
     *
     * <p><b>Advisory only today.</b> It is sent on the wire but the client does not act
     * on it: item appearance comes from the synced assets, so the model that matters is
     * the one at {@code assets/<namespace>/items/<path>.json} in your jar. Setting this
     * to something other than your real model changes nothing.
     */
    public ItemRegistration model(String modelId) {
        this.modelId = modelId;
        return this;
    }

    /**
     * Maximum stack size (default 64).
     */
    public ItemRegistration maxStackSize(int maxStackSize) {
        this.maxStackSize = maxStackSize;
        return this;
    }

    /**
     * Whether the item has an enchantment glint.
     *
     * <p><b>Advisory only today.</b> Sent on the wire, not acted on by the client; put
     * the glint on the real server-side item's components instead.
     */
    public ItemRegistration hasGlint(boolean hasGlint) {
        this.hasGlint = hasGlint;
        return this;
    }

    /**
     * Say this item is a tool, so the client mines with it the way the server does.
     *
     * <p>Without this the client's stand-in for a synced item is a bare {@code new Item(...)}:
     * no tool component, so it swings at hand speed and the block takes as long as it would
     * bare-handed. The server knows better and the two disagree for the whole dig.
     *
     * <p>What crosses the wire is the material's own numbers, and the client feeds them back
     * through the same {@code Properties.axe}/{@code pickaxe}/... vanilla uses. Rebuilding it
     * from the recipe rather than shipping a finished component is what keeps the two ends
     * identical: there is no second implementation to drift.
     *
     * @param kind one of {@code axe}, {@code pickaxe}, {@code shovel}, {@code hoe}, {@code sword}
     */
    public ItemRegistration tool(String kind, ToolMaterial material,
            float attackDamage, float attackSpeed) {
        this.toolSpec = String.join("|",
            kind,
            material.incorrectBlocksForDrops().location().toString(),
            String.valueOf(material.durability()),
            String.valueOf(material.speed()),
            String.valueOf(material.attackDamageBonus()),
            String.valueOf(material.enchantmentValue()),
            material.repairItems().location().toString(),
            String.valueOf(attackDamage),
            String.valueOf(attackSpeed));
        return this;
    }

    /** The wire form of {@link #tool}, or empty for anything that is not a tool. */
    public String getToolSpec() { return toolSpec; }

    public String getModelId() { return modelId; }
    public int getMaxStackSize() { return maxStackSize; }
    public boolean hasGlint() { return hasGlint; }
}
