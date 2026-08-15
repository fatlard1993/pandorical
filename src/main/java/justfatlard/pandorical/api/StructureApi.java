package justfatlard.pandorical.api;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;

/**
 * API for server mods to display a moving, rotating cluster of blocks ("structure") to
 * Pandorical clients as one batch-rendered object, e.g. a ship a player can ride and steer.
 *
 * <p>Unlike {@link ScreenApi}/{@link HudApi}/{@link CameraApi}, which are per-player, structures
 * are broadcast objects: anchored to a real server {@link Entity} and sent automatically to
 * every player that tracks it (via Fabric's EntityTrackingEvents), so a single
 * {@link #spawn}/{@link #updatePose}/{@link #updateBlocks}/{@link #setVisible} call reaches
 * every current and future tracker. Callers never pass a {@code ServerPlayer}.
 *
 * <p>{@code structureId} must be unique across the whole server (not scoped to a player or
 * the anchor entity): namespace it, e.g. {@code "bigboats:" + shipUuid}.
 *
 * <p>All calls are no-ops for players whose client lacks the {@code "structures"} capability.
 *
 * <p>Pandorical does not track the anchor entity's lifecycle beyond tracking start/stop:
 * call {@link #despawn} when the anchor entity is permanently removed, or server-side
 * state leaks.
 */
public interface StructureApi {
    /**
     * Register and broadcast a new structure anchored to {@code anchorEntity}.
     * Sent immediately to every player currently tracking {@code anchorEntity}, and to any
     * player who starts tracking it afterward.
     *
     * @param anchorEntity the real server entity whose tracking radius drives visibility
     * @param structureId  a server-wide unique id for this structure
     * @param blocks       the blocks making up the structure, relative to the origin
     * @param initialPose  the structure's initial world position and yaw
     */
    void spawn(Entity anchorEntity, String structureId, List<BlockEntry> blocks, StructurePose initialPose);

    /**
     * Push a new world position/yaw for an existing structure. Clients interpolate towards
     * this pose from the previously known one, so call this as often as the structure moves
     * (e.g. once per server tick) for smooth motion.
     * No-op if {@code structureId} is unknown.
     */
    void updatePose(String structureId, StructurePose pose);

    /**
     * Apply incremental block changes. Pass an empty list/map for any dimension that
     * isn't changing.
     * No-op if {@code structureId} is unknown.
     *
     * @param added   new blocks to add (a {@link RelPos} already present is overwritten)
     * @param removed relative positions to remove entirely
     * @param changed relative positions whose {@link BlockState} changes (must already exist)
     */
    void updateBlocks(String structureId, List<BlockEntry> added, List<RelPos> removed, Map<RelPos, BlockState> changed);

    /**
     * Show or hide a structure without despawning it, e.g. hide the virtual structure while
     * a docked ship's real placed-world blocks are visible instead. Server-side block and
     * pose state is retained while hidden.
     * No-op if {@code structureId} is unknown.
     */
    void setVisible(String structureId, boolean visible);

    /**
     * Permanently remove a structure and forget its server-side state. Broadcast to every
     * current tracker of the anchor entity.
     * No-op if {@code structureId} is unknown.
     */
    void despawn(String structureId);
}
