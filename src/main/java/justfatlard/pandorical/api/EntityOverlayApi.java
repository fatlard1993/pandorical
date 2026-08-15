package justfatlard.pandorical.api;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/**
 * API for server mods to overlay an extra texture on top of a living entity's
 * model for Pandorical clients, e.g. dressing a particular villager in gloves.
 * The overlay is rendered as an additional cutout layer using the entity's own
 * model, so the texture must follow the entity's texture layout (transparent
 * pixels are simply not drawn).
 *
 * <p>Like {@link StructureApi}, overlays are broadcast objects keyed off real
 * entity tracking: one {@link #set} call reaches every player currently
 * tracking the entity and every player who starts tracking it later. Callers
 * never pass a {@code ServerPlayer}.
 *
 * <p>All sends are no-ops for players whose client lacks the
 * {@code "entity_overlays"} capability; vanilla clients are unaffected.
 *
 * <p>State is kept in memory keyed by entity UUID and dropped when the entity
 * unloads (despawn, death, or chunk unload). It does not persist: a mod whose
 * overlay should survive reloads must call {@link #set} again when its entity
 * loads, typically from a tick or load hook that re-reads its own persisted
 * flag.
 */
public interface EntityOverlayApi {
    /**
     * Set (or replace) the overlay texture for an entity. Pushed immediately
     * to all current trackers and automatically to future trackers.
     *
     * @param entity  the living entity to overlay
     * @param texture full texture identifier including extension, following
     *                the entity model's texture layout, e.g.
     *                {@code Identifier.fromNamespaceAndPath("mymod", "textures/entity/my_overlay.png")}
     */
    void set(Entity entity, Identifier texture);

    /** Remove an entity's overlay and notify all current trackers. No-op if none set. */
    void clear(Entity entity);
}
