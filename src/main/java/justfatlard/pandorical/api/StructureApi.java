package justfatlard.pandorical.api;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;

/**
 * A moving, rotating cluster of blocks that Pandorical clients draw as one object, e.g. a ship.
 *
 * <p>Broadcast off real entity tracking: a structure is anchored to a server {@link Entity}, and
 * each call reaches every player tracking the anchor now or later. Callers never pass a player.
 * Players without the {@code "structures"} capability are sent nothing.
 *
 * <p>{@code structureId} is unique across the whole server, so namespace it, e.g.
 * {@code "bigboats:" + shipUuid}. Calls naming an unknown id are no-ops.
 *
 * <p>Pandorical does not watch the anchor's lifecycle: call {@link #despawn} when the anchor is
 * removed for good, or the server-side state leaks.
 */
public interface StructureApi {
    /**
     * <b>The anchor cannot be a player who should see the structure.</b> A player is not among
     * their own trackers, so that player never sees it, and nothing errors. Anchor to the boat,
     * the mob, or a marker entity spawned for the purpose.
     *
     * @param anchorEntity the server entity whose tracking radius drives visibility
     */
    void spawn(Entity anchorEntity, String structureId, List<BlockEntry> blocks, StructurePose initialPose);

    /**
     * Clients interpolate from the last pose, so call this as often as the structure moves.
     *
     * <p>Sent at the start of the anchor level's next tick, with the entity positions; only the
     * last pose set within a tick is sent. An {@link #updateBlocks} or {@link #setVisible} for
     * the same structure sends a waiting pose first, so clients see the calls in order.
     */
    void updatePose(String structureId, StructurePose pose);

    /**
     * Pass an empty list or map for what isn't changing.
     *
     * @param added   a {@link RelPos} already present is overwritten
     * @param changed positions that must already exist
     */
    void updateBlocks(String structureId, List<BlockEntry> added, List<RelPos> removed, Map<RelPos, BlockState> changed);

    /** Server-side blocks and pose are kept while hidden. */
    void setVisible(String structureId, boolean visible);

    /** Remove the structure for every tracker and forget its server-side state. */
    void despawn(String structureId);
}
