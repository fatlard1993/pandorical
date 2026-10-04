package justfatlard.pandorical.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

/** What happens when a player uses a block of a walkable structure; see {@link StructureApi#onBlockUse}. */
@FunctionalInterface
public interface StructureUseHandler {
    /**
     * @param anchor the entity the structure was spawned with
     * @param pos    the block's position in the structure's own frame
     * @param state  the block as the structure holds it
     * @return PASS to let the next handler answer
     */
    InteractionResult use(ServerPlayer player, Entity anchor, String structureId, RelPos pos, BlockState state);
}
