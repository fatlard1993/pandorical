package justfatlard.pandorical.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block that sounds and crumbles like another, decided position by position: a model of
 * something built inside one block, heard and broken as what it is mostly made of.
 *
 * <p>A Pandorical client's stand-in for a server block takes its sounds and crumbs from the
 * vanilla block it is based on, which knows nothing about one particular placement. Set a
 * material here and a client with the {@code "block_materials"} capability uses that block state's
 * sounds and particles at that position for what it plays itself: the player's own steps and
 * landings on it, the hits and cracks while it is mined, and the break as it goes, the breaker's
 * own included. What the server plays to everyone is the server's to choose: a sound it sends is
 * already a sound, and is not looked up again.
 *
 * <p>The client keeps a material until the server clears it, so the block's own break, which the
 * breaker's client plays before the server has heard of it, still finds it. Kept in memory only,
 * per world, and told to every player in that world: set it again as the block loads, and clear
 * it when the block goes, or whatever is built there next sounds like the old block.
 */
public interface BlockMaterialApi {
    /** @param material any block state but air; clear the position instead of setting air */
    void setMaterial(ServerLevel level, BlockPos pos, BlockState material);

    void clearMaterial(ServerLevel level, BlockPos pos);
}
