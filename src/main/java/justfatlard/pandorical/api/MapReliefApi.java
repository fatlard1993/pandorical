package justfatlard.pandorical.api;

import net.minecraft.world.entity.decoration.ItemFrame;

/**
 * Maps in item frames drawn as the ground they show: the {@link MapTerrain} under each pixel
 * standing out of the frame in miniature, every block in its own colours and tinted by its biome.
 *
 * <p>The terrain belongs to the frame, not the map: a frame whose map is swapped keeps showing
 * the old ground until the caller shows it again.
 *
 * <p>Like {@link PictureApi}, a relief is broadcast off entity tracking: one {@link #show}
 * reaches every player tracking the frame now and every player who starts to later. Clients
 * without relief support draw the map flat, as vanilla does.
 *
 * <p>State is kept in memory by the frame's UUID and dropped when the frame unloads: a mod whose
 * relief should outlive a reload calls {@link #show} again when its frame loads.
 *
 * <p>New in 1.3.10 and shaped around one user so far; it may still change shape.
 */
public interface MapReliefApi {
    /** Pixels to a side of a map, and so columns to a side of a relief. */
    int SIDE = 128;

    /** Draw the map in this frame as this ground, replacing any it had, as it stands. */
    void show(ItemFrame frame, MapTerrain terrain);

    /**
     * As {@link #show}, but the ground rises out of the flat map for everyone watching, rather
     * than being there already. Players who come by later see it standing.
     */
    void raise(ItemFrame frame, MapTerrain terrain);

    /** Draw the map in this frame flat again, at once. No-op if it has no relief. */
    void clear(ItemFrame frame);

    /** As {@link #clear}, but the ground sinks back into the flat map rather than vanishing. */
    void lower(ItemFrame frame);
}
