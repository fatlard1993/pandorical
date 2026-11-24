package justfatlard.pandorical.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Blocks an explosion leaves standing, one block in one place at a time, which a block's blast
 * resistance cannot say: a glued block, a locked grave, a chest still holding its loot.
 *
 * <p>Asked as the explosion is about to break what it found, so a block put down after the blast
 * decided its area, a player's head say, is asked about too. One hook for every mod, rather than each
 * patching explosions its own way.
 */
public interface BlastApi {
    /** Call once, at init. Asked for every block every explosion would break, so keep it cheap. */
    void protect(Guard guard);

    @FunctionalInterface
    interface Guard {
        /** Whether the block at this position stays. */
        boolean spares(ServerLevel level, BlockPos pos);
    }
}
