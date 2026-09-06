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
    void mark(ServerLevel level, BlockPos pos, String mark);

    void unmark(ServerLevel level, BlockPos pos, String mark);

    boolean isMarked(ServerLevel level, BlockPos pos, String mark);
}
