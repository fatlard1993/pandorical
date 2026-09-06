package justfatlard.pandorical.api;

/**
 * API for registering block tint (color) mappings.
 * Registrations are synced to connecting clients during the configuration phase.
 * Call from your mod's {@code onInitialize()}.
 */
public interface BlockTintApi {
    void grass(String... blockIds);
    void stem(String... blockIds);
    void sugarCane(String... blockIds);
    void foliage(String... blockIds);
    void constant(int argb, String... blockIds);

    /**
     * Declare that these blocks take their colour from where they are, not from what they are.
     *
     * <p>Call from {@code onInitialize()} like the rest: this is the config-phase half, and it
     * only says which blocks will ever be asked. The colours themselves arrive later, per player,
     * through {@link #paint}.
     *
     * <p><b>The block's model has to ask for a tint.</b> A tint only reaches faces carrying a
     * {@code tintindex}, and most vanilla models carry none - nether portal included. A mod
     * painting a vanilla block is also on the hook for shipping a model override that adds one,
     * through {@link ContentApi#registerAsset} under the {@code minecraft} namespace, which the
     * synced pack serves above vanilla's own. Without that this does nothing and says nothing,
     * because there is no moment at which anything can tell that it did not work.
     */
    void positional(String... blockIds);

    /**
     * Per-position tint with a colour for every position nobody has painted. A block whose
     * texture is drained to grey so the tint can be its whole colour needs this, or every
     * unpainted one draws grey.
     */
    void positional(int fallbackArgb, String... blockIds);

    /**
     * Paint blocks for one player. Positions not mentioned are left alone.
     *
     * <p>Per player because that is the shape everything visual here has: two players can be
     * owed different pictures of the same world. A mod that means "everyone" sends it to
     * everyone, and on join, because nothing here survives a reconnect.
     *
     * <p>No-op for a client that has not registered for it, so a player on an older Pandorical
     * simply sees the block's ordinary colour.
     */
    void paint(net.minecraft.server.level.ServerPlayer player,
        java.util.Map<net.minecraft.core.BlockPos, Integer> argbByPosition);

    /** Put these positions back to their ordinary colour. */
    void unpaint(net.minecraft.server.level.ServerPlayer player,
        java.util.Collection<net.minecraft.core.BlockPos> positions);
}
