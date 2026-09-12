package justfatlard.pandorical.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * A word on a block position that every client can read, for what a block's state cannot carry.
 *
 * <p>Kept in memory per level, pushed to everyone in the level as marks change, and sent whole to
 * anyone who arrives or changes level. Not saved: to keep a mark across a restart, keep your own
 * record and mark again when the level loads.
 */
public interface BlockMarkApi {
    /**
     * A fence or wall that joins nothing beside it, and nothing beside it joins to it, on server
     * and client alike. Marking a block already standing does not reshape it: update it and its
     * neighbours ({@code Block.updateFromNeighbourShapes}) after marking.
     */
    String POST = "post";

    /**
     * A fence gate hung as one leaf from its own left post, drawn open as that one leaf. Only for
     * a gate with no gate beside it: a pair opens from the middle.
     */
    String GATE_HINGE_LEFT = "moredoor:gate_left";

    /** As {@link #GATE_HINGE_LEFT}, from the right post. */
    String GATE_HINGE_RIGHT = "moredoor:gate_right";

    /** Name a mark of your own {@code yourmod:thing}. */
    void mark(ServerLevel level, BlockPos pos, String mark);

    void unmark(ServerLevel level, BlockPos pos, String mark);

    boolean isMarked(ServerLevel level, BlockPos pos, String mark);
}
