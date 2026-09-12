package justfatlard.pandorical.api;

import net.minecraft.world.item.ToolMaterial;

public class ItemRegistration {
    private String modelId = "";
    private String toolSpec = "";
    private int maxStackSize = 64;
    private boolean hasGlint = false;

    /**
     * Not acted on by the client: appearance comes from
     * {@code assets/<namespace>/items/<path>.json} in your jar.
     */
    public ItemRegistration model(String modelId) {
        this.modelId = modelId;
        return this;
    }

    public ItemRegistration maxStackSize(int maxStackSize) {
        this.maxStackSize = maxStackSize;
        return this;
    }

    /** Not acted on by the client; put the glint on the server-side item's components. */
    public ItemRegistration hasGlint(boolean hasGlint) {
        this.hasGlint = hasGlint;
        return this;
    }

    /**
     * Declare the item a tool, so the client mines with it as the server does. Without it the
     * client's stand-in has no tool component and digs at bare-hand speed.
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

    public String getToolSpec() { return toolSpec; }

    public String getModelId() { return modelId; }
    public int getMaxStackSize() { return maxStackSize; }
    public boolean hasGlint() { return hasGlint; }
}
