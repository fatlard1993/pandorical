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
     * @param added   a {@link RelPos} already present is overwritten, drawing and all, so passing
     *                an entry here is how a block's {@link BlockEntry#drawing} is changed
     * @param changed positions that must already exist; each keeps the drawing it has
     */
    void updateBlocks(String structureId, List<BlockEntry> added, List<RelPos> removed, Map<RelPos, BlockState> changed);

    /**
     * Answer a player using a block of a walkable structure, such as a door on a ship at sea. A
     * Pandorical client sends the use when the block under its crosshair is in the block tag
     * {@code pandorical:usable_on_structures} and nearer than anything in the world. Pandorical
     * fills that tag with nothing: the mod that answers the use tags the blocks it answers for. The
     * server holds the block to the player's reach, against where the structure stood over its last
     * few poses, and hands it to each handler in turn. Spectators and the dead are never handed on.
     */
    void onBlockUse(StructureUseHandler handler);

    /** Server-side blocks and pose are kept while hidden. */
    void setVisible(String structureId, boolean visible);

    /**
     * Make a structure something to stand on: solid to the local player of a client with the
     * {@code "walkable_structures"} capability, which also carries them with it while they stand
     * on it or jump from it, and solid on the server to every entity that is not a player - mobs,
     * items, anything moved there - with each block's own collision shape. Hidden or scaled
     * structures are neither. Nothing is made solid for a player whose client lacks the
     * capability: they cannot stand on a moving structure.
     *
     * <p>Players stand on it on the client because it cannot be done from the server: a player's
     * position is the client's, and anything the server does to move them arrives on the network's
     * schedule rather than the frame's, so a player carried from the server lags and jitters
     * against the deck they can see. The server should leave such a player's position alone while
     * they are aboard. What the server carries that is not a player, it moves itself.
     */
    void setWalkable(String structureId, boolean walkable);

    /**
     * Keep the sea out of these cells of a structure, such as the hold of a ship at sea, which
     * stands where the world has water: nothing in them is in water, on the server or on a client
     * with the {@code "dry_structures"} capability, which also stops drawing the water's surface
     * across them. Replaces any cells set before; pass an empty collection for none. Hidden
     * structures keep nothing dry.
     *
     * <p>A cell is dry for what is in it, not for the world: the water is still there, and flows,
     * freezes and is drawn everywhere else as ever. Mark the structure's own cells at and below the
     * water as well as the space they enclose, or something pressed against the hull, in a cell
     * the turned structure only partly covers, is wet.
     */
    void setDry(String structureId, java.util.Collection<RelPos> cells);

    /**
     * Draw a structure at this scale about its pose: 1 is full size, and 1/8 draws every block an
     * eighth of a block across, for a model of something rather than the thing itself. Sent to
     * clients with the {@code "scaled_structures"} capability; one without it draws full size.
     *
     * <p>Only the drawing scales, about the pose's point: block (0, 0, 0) of the structure keeps
     * its lowest corner there. Walking on it, using its blocks and keeping it dry all still take it
     * at full size, so a scaled structure should be neither walkable nor dry. Each block takes its
     * light from where it is drawn.
     *
     * <p>A {@link #spawn} starts the structure afresh at full size, so set the scale again after
     * every spawn, including one that rebuilds a structure as its anchor loads.
     *
     * @param scale above nought; 1 is full size
     */
    void setScale(String structureId, float scale);

    /**
     * Who draws which blocks as a build: register a provider for blocks your mod owns, or make
     * entries through it for blocks your structure carries. A {@link BlockEntry} with a drawing
     * reaches clients with the {@code "structure_cells"} capability as that build, at 1/n of its
     * block, turned and scaled with the structure and lit where it stands; every other client
     * draws the block. Walking on it, using it and keeping it dry take the block either way.
     */
    StructureDrawings drawings();

    /** Remove the structure for every tracker and forget its server-side state. */
    void despawn(String structureId);
}
