package justfatlard.pandorical.api;

import net.minecraft.world.entity.Entity;

/**
 * Pictures standing in the world, anchored to entities, that can be painted and seen changing.
 *
 * <p>Broadcast off real entity tracking: one {@link #show} reaches every player tracking the
 * anchor now or later, and a player who stops tracking it has it taken down. Callers never pass
 * a player. Clients without Pandorical's picture support are sent nothing.
 *
 * <p>Kept in memory by the anchor's UUID and dropped when the anchor unloads. Not persisted: a
 * mod whose picture should outlive a reload keeps its own cells and calls {@link #show} again
 * when the anchor loads.
 *
 * <p>Not yet stable: it may still change shape.
 */
public interface PictureApi {
    /** Replaces any picture the anchor had. */
    void show(Entity anchor, Picture picture);

    /**
     * Change some cells without sending the rest again.
     *
     * @param indices cells, as indices into the picture's cells
     * @param values  the palette index each becomes
     */
    void paint(Entity anchor, int[] indices, byte[] values);

    /** No-op if the anchor has none. */
    void clear(Entity anchor);
}
