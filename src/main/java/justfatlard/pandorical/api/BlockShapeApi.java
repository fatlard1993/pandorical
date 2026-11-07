package justfatlard.pandorical.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * A block whose shape is decided position by position, not by its state: a model of something
 * built inside one block, solid only where the model is.
 *
 * <p>A Pandorical client's stand-in for a server block takes its shape from a vanilla block's
 * class, which knows nothing about what one particular placement holds. Set a shape here and a
 * client with the {@code "block_shapes"} capability collides with, and targets, exactly these
 * boxes at that position until the shape is cleared.
 *
 * <p>The server block has to answer with the same shape itself, or the two sides disagree and the
 * player rubber-bands. Give the block {@code Properties#dynamicShape()} so its shape is not cached
 * by state, and in {@code getShape} and {@code getCollisionShape} answer a player without the
 * capability with a plain block, which is what their client takes it for:
 *
 * <pre>{@code
 * if (context instanceof EntityCollisionContext entity && entity.getEntity() instanceof ServerPlayer player
 *         && !PandoricalApi.hasCapability(player, Capabilities.BLOCK_SHAPES)) return Shapes.block();
 * return theShapeYouSet;
 * }</pre>
 *
 * <p>Kept in memory only, per world, and told to every player in that world: set it again as the
 * block loads (from whatever loads with it), and clear it when the block goes, or the client goes
 * on colliding with the shape over whatever is built there next.
 */
public interface BlockShapeApi {
    /** @param boxes at least one, in the block's own space, each within 0 to 1 on every axis */
    void setShape(ServerLevel level, BlockPos pos, List<AABB> boxes);

    void clearShape(ServerLevel level, BlockPos pos);
}
