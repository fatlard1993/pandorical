package justfatlard.pandorical.api;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/**
 * An extra texture drawn over an entity's model, as a cutout layer on the entity's own model:
 * the texture follows the entity's texture layout, and transparent pixels are not drawn. Only
 * living entities and minecarts draw it.
 *
 * <p>Broadcast off real entity tracking: one {@link #set} reaches every player tracking the
 * entity now or later. Callers never pass a player. Players without the
 * {@code "entity_overlays"} capability are sent nothing.
 *
 * <p>Kept in memory by entity UUID and dropped when the entity unloads (despawn, death, chunk
 * unload). Not persisted: to keep an overlay across reloads, call {@link #set} again when the
 * entity loads.
 */
public interface EntityOverlayApi {
    /**
     * Set or replace an entity's overlay.
     *
     * @param texture full texture id including extension, e.g.
     *                {@code Identifier.fromNamespaceAndPath("mymod", "textures/entity/my_overlay.png")}
     */
    void set(Entity entity, Identifier texture);

    /** No-op if none is set. */
    void clear(Entity entity);
}
