package justfatlard.pandorical.api;

import net.minecraft.world.entity.Entity;

/**
 * Pictures standing in the world, anchored to entities: a canvas on an easel, a sign board, a
 * picture anything can be painted onto and seen changing.
 *
 * <p>Like {@link EntityOverlayApi}, a picture is broadcast off real entity tracking: one
 * {@link #show} reaches every player tracking the anchor now and every player who starts to
 * later, and a player who stops tracking it has it taken down. Callers never pass a player.
 * The anchor can be anything tracked; an invisible display entity is the usual choice, since it
 * is persistent, draws nothing of its own and cannot be hit.
 *
 * <p>Clients without Pandorical's picture support are never sent one.
 *
 * <p>State is kept in memory by the anchor's UUID and dropped when the anchor unloads. It does
 * not persist: a mod whose picture should outlive a reload keeps its own cells and calls
 * {@link #show} again when its anchor loads.
 *
 * <p>New in 1.3.9 and shaped around one user so far; it may still change shape.
 */
public interface PictureApi {
    /** Show a picture on an entity, replacing any it had. */
    void show(Entity anchor, Picture picture);

    /**
     * Change some cells of the picture an entity carries, without sending the rest again. For a
     * picture being painted: send what each stroke changed.
     *
     * @param indices cells, as indices into the picture's cells
     * @param values  the palette index each becomes
     */
    void paint(Entity anchor, int[] indices, byte[] values);

    /** Take the picture off an entity. No-op if it has none. */
    void clear(Entity anchor);
}
