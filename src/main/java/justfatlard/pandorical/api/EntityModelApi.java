package justfatlard.pandorical.api;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Draw a living entity with a model your mod ships: new geometry in place of its own, or its own
 * geometry with parts moved and resized, or both.
 *
 * <p><b>The model is content.</b> A JSON file at
 * {@code assets/<namespace>/pandorical/entity_models/<path>.json}, shipped with your other assets by
 * {@link ContentApi#registerModAssets} (or {@link ContentApi#registerAsset}), and named here by the
 * id {@code <namespace>:<path>}. It can carry:
 * <ul>
 * <li>{@code parts}: geometry, drawn in place of the entity's own model - a tree of named parts, each
 * with a {@code pivot}, a {@code rotation} in degrees, a {@code scale} and {@code cubes}
 * ({@code origin}, {@code size}, {@code uv}, {@code inflate}, {@code mirror}), on a texture of
 * {@code texture_size}. In the game's own model units: sixteen to a block, y down, the feet at 24.</li>
 * <li>{@code transforms}: per part, by name, {@code scale} then {@code offset}, applied to the pose the
 * game's animation gives each top-level part, every frame, on every model drawn for the entity - its
 * body, its armour, its clothes. The game builds its own model variants the same way (its mesh
 * transformers scale and translate part poses).</li>
 * <li>{@code texture}: a texture to draw it with.</li>
 * </ul>
 *
 * <p><b>Animation is the entity's own.</b> New geometry is built into a fresh instance of the model
 * class the entity's renderer already draws with, so it walks, aims and swings as before, provided it
 * has every part that class reaches for by name - for a humanoid mob {@code head} (with a child
 * {@code hat}), {@code body}, {@code right_arm}, {@code left_arm}, {@code right_leg}, {@code left_leg}.
 * Held items and worn heads follow those parts. Geometry that lacks one is drawn standing still.
 * Transforms always keep the animation, and the layers too, which is the reason to prefer them when
 * the entity's own texture will do. Held items are kept at their own size on a resized arm.
 *
 * <p><b>Size is two things.</b> The hitbox is the server's, from the scale attribute, and is what
 * every client without this feature draws. {@code drawScale} multiplies only the drawn size on a
 * client that has it - so a mod that halved the attribute for a smaller hitbox, and ships geometry
 * that is small already, passes 2 to undo the half in the drawing.
 *
 * <p>Broadcast off real entity tracking: one {@link #set} reaches every player tracking the entity
 * now or later, and callers never pass a player. Sent only to clients that registered
 * {@code pandorical:entity_model} (new in 15.16); there is no capability string. A client that cannot
 * find or read the model draws the entity as it was and reports {@link NotUnderstood#ENTITY_MODEL}.
 * Kept in memory by UUID and dropped when the entity unloads: set it again when the entity loads.
 */
public interface EntityModelApi {

    /**
     * Set or replace the entity's model.
     *
     * @param model     {@code <namespace>:<path>}, for {@code assets/<namespace>/pandorical/entity_models/<path>.json}
     * @param texture   a full texture id, extension included, or null for the model's own if it names
     *                  one, else the entity's
     * @param drawScale multiplies the drawn size and not the hitbox; above nought
     */
    void set(LivingEntity entity, Identifier model, @Nullable Identifier texture, float drawScale);

    /** {@link #set(LivingEntity, Identifier, Identifier, float)} with the model's texture, at the entity's own size. */
    default void set(LivingEntity entity, Identifier model) {
        set(entity, model, null, 1F);
    }

    /** No-op if none is set. */
    void clear(LivingEntity entity);

    /** The model set on the entity on this server, or null. */
    @Nullable Identifier get(LivingEntity entity);
}
