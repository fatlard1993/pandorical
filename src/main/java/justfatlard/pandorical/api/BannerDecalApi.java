package justfatlard.pandorical.api;

import java.util.Collection;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

/**
 * Banner patterns laid flat on a block, drawn by the client over the block's own texture: only
 * the pattern layers, no base colour.
 *
 * <p>Per player and per position, sent as deltas. A client draws a decal only while the chunk is
 * loaded and the block is still there. Vanilla clients see nothing; a vanilla-visible fallback
 * such as an item display can be hidden from Pandorical clients with {@link #HIDDEN_ITEM_KEY}.
 */
public interface BannerDecalApi {
    /** Custom-data key on an item display's item that Pandorical clients take as "do not draw". */
    String HIDDEN_ITEM_KEY = "pandorical:hidden";

    /**
     * @param pos      the block the pattern is anchored to
     * @param toHead   the direction the pattern's top points; the face of {@code pos} on that side
     *                 is where {@code fromHead} is measured from
     * @param lift     height of the pattern above the bottom of {@code pos}, in blocks
     * @param fromHead inset from the head-side face to the pattern's top edge, in blocks
     * @param length   how far the pattern runs away from the head, in blocks; may cross into the
     *                 next block
     * @param width    across, centred on the block, in blocks
     */
    record Decal(BlockPos pos, Direction toHead, float lift, float fromHead, float length, float width,
            BannerPatternLayers layers) {}

    void send(ServerPlayer player, Collection<Decal> decals);

    default void send(ServerPlayer player, Decal decal) { send(player, List.of(decal)); }

    void clear(ServerPlayer player, Collection<BlockPos> positions);

    default void clear(ServerPlayer player, BlockPos pos) { clear(player, List.of(pos)); }
}
