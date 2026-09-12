package justfatlard.pandorical.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * A word on a block position that every client can read.
 *
 * <p>For the facts a block cannot carry in its state and a client-side renderer needs anyway: a
 * door leaf cut loose from its neighbours, say. Marks are kept in memory per level, pushed to
 * everyone in that level as they change, and sent whole to anyone who arrives or changes level.
 * They are not saved: a mod that wants a mark to outlive a restart keeps its own record and
 * marks again when the level loads.
 */
public interface BlockMarkApi {
    /**
     * A fence or wall that stands alone. Marked so, it joins nothing beside it and nothing
     * beside it joins to it: a post, in the shape of whatever fence or wall it is. Pandorical
     * applies the mark to the block's shape on both sides whenever the shape is worked out; who
     * marks and who remembers is the mod that asks for it. Marking a block already standing does
     * not reshape it, so update it and its neighbours ({@code Block.updateFromNeighbourShapes})
     * after marking.
     */
    String POST = "post";

    /**
     * A fence gate hung as one leaf from its left post, left being the gate's own as it faces.
     * Open, it is drawn as that one leaf. Only for a gate with no gate beside it: a pair opens
     * from the middle.
     */
    String GATE_HINGE_LEFT = "moredoor:gate_left";

    /** As {@link #GATE_HINGE_LEFT}, from the right post. */
    String GATE_HINGE_RIGHT = "moredoor:gate_right";

    /**
     * Mark a block, and tell every Pandorical client in its level. Marks live as long as the
     * server runs; a mod that wants one kept across restarts stores it and marks again at start.
     * Name a mark of your own {@code yourmod:thing}.
     */
    void mark(ServerLevel level, BlockPos pos, String mark);

    /** Take a mark off a block, and tell every Pandorical client in its level. */
    void unmark(ServerLevel level, BlockPos pos, String mark);

    /** Whether the block carries the mark, as this server knows it. */
    boolean isMarked(ServerLevel level, BlockPos pos, String mark);
}
