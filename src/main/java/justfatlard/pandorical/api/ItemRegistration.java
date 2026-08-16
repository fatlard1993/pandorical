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

    public String getModelId() { return modelId; }
    public int getMaxStackSize() { return maxStackSize; }
    public boolean hasGlint() { return hasGlint; }
}
