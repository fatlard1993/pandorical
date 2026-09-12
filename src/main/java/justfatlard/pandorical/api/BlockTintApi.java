package justfatlard.pandorical.api;

import java.util.Collection;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * Block tint mappings, synced to connecting clients during the configuration phase. Register
 * from {@code onInitialize()}.
 */
public interface BlockTintApi {
    void grass(String... blockIds);
    void stem(String... blockIds);
    void sugarCane(String... blockIds);
    void foliage(String... blockIds);
    void constant(int argb, String... blockIds);

    /**
     * Declare blocks whose colour depends on position; the colours arrive per player through
     * {@link #paint}.
     *
     * <p><b>The block's model has to ask for a tint.</b> A tint only reaches faces carrying a
     * {@code tintindex}, and most vanilla models, nether portal included, carry none. To paint a
     * vanilla block, also ship a model override that adds one, through
     * {@link ContentApi#registerAsset} under the {@code minecraft} namespace. Without it nothing
     * is tinted and nothing reports it.
     */
    void positional(String... blockIds);

    /**
     * As {@link #positional(String...)}, with a colour for every position nobody has painted,
     * e.g. for a grey texture the tint colours whole.
     */
    void positional(int fallbackArgb, String... blockIds);

    /**
     * Paint blocks for one player; positions not mentioned are left alone. Nothing survives a
     * reconnect, so paint again each session. A client too old for it keeps the ordinary colour.
     */
    void paint(ServerPlayer player,
        Map<BlockPos, Integer> argbByPosition);

    void unpaint(ServerPlayer player,
        Collection<BlockPos> positions);
}
