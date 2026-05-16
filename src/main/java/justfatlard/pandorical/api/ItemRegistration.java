package justfatlard.pandorical.api;

/**
 * Builder for custom item registration.
 */
public class ItemRegistration {
    private String modelId = "";
    private int maxStackSize = 64;
    private boolean hasGlint = false;

    /**
     * Model resource location (e.g., "big-boats:item/christening_bottle").
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
     */
    public ItemRegistration hasGlint(boolean hasGlint) {
        this.hasGlint = hasGlint;
        return this;
    }

    public String getModelId() { return modelId; }
    public int getMaxStackSize() { return maxStackSize; }
    public boolean hasGlint() { return hasGlint; }
}
