package justfatlard.pandorical.api;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

/**
 * A new entity, drawn on Pandorical clients from your mod's own model, or as a vanilla one it is
 * built on.
 *
 * <p>The entity type is a real one, registered by the mod in the usual way, with its own class,
 * goals, attributes, spawns and loot. What a client cannot be handed is the class itself, so it
 * stands something in for it, in one of two ways.
 *
 * <p><b>Its own body</b> (no {@link #base}): the client runs a generic mob of the entity's size and
 * draws it entirely from {@link #model}, which is required - geometry, skin, and the animations the
 * model file names. The entity's class must be a {@code Mob}; anything it syncs is carried, values
 * of its own included. This is the way to add an animal the game has nothing like.
 *
 * <p><b>On a base</b>: the client runs the {@link #base}'s class, at the base's size, drawn by the
 * base's renderer and dressed with whatever {@link #model} and {@link #texture} say. The server's
 * class must then extend the base's class and add no synced data of its own, because the client
 * decodes the entity's data by the base's layout. Pandorical checks the first one of each type that
 * loads and says so loudly if it breaks this.
 *
 * <p><b>Vanilla clients</b> are not sent the entity at all unless it has a {@link #vanillaFallback},
 * in which case they are sent that vanilla mob instead. A fallback must sync at least everything the
 * entity does, the same way: an entity whose class sits under the fallback's parent and adds no
 * synced data of its own will do. One that does not is not shown to vanilla clients, and the server
 * log says why.
 */
public final class EntityRegistration {
    private @Nullable EntityType<?> base;
    private @Nullable EntityType<?> fallback;
    private boolean fallbackToBase;
    private @Nullable Identifier model;
    private @Nullable Identifier texture;
    private @Nullable Identifier babyTexture;
    private float drawScale = 1F;

    /** The vanilla type the client stands in: its class, size and renderer. Without one, the entity has its own body. */
    public EntityRegistration base(EntityType<?> base) {
        this.base = base;
        return this;
    }

    /**
     * Geometry for the whole type, as {@link EntityModelApi} reads it:
     * {@code assets/<namespace>/pandorical/entity_models/<path>.json}. Living entities only.
     * Required without a base, where its {@code animation} block is what moves it. On a base, one
     * set on a single entity through {@link EntityModelApi} wins over this.
     *
     * <p>It stands in for the baby's model too, so a baby wears {@link #texture} rather than
     * {@link #babyTexture}, and is drawn at its age scale.
     */
    public EntityRegistration model(Identifier model) {
        this.model = model;
        return this;
    }

    /** The skin, laid out for {@link #model}, or for the base's model if there is none. */
    public EntityRegistration texture(Identifier texture) {
        this.texture = texture;
        return this;
    }

    /**
     * The skin while it is a baby, on a base with no {@link #model}. The game draws most babies
     * with a model of their own that lays its texture out differently, so the adult skin on a baby
     * lands on the wrong boxes.
     */
    public EntityRegistration babyTexture(Identifier babyTexture) {
        this.babyTexture = babyTexture;
        return this;
    }

    /** Multiplies the drawn size and not the hitbox; above nought. */
    public EntityRegistration drawScale(float drawScale) {
        if (!(drawScale > 0F)) throw new IllegalArgumentException("drawScale must be above nought: " + drawScale);
        this.drawScale = drawScale;
        return this;
    }

    /** Vanilla clients are sent this vanilla mob, rather than nothing. */
    public EntityRegistration vanillaFallback(EntityType<?> fallback) {
        this.fallback = fallback;
        return this;
    }

    /** Vanilla clients are sent the {@link #base}, rather than nothing. */
    public EntityRegistration vanillaFallback() {
        this.fallbackToBase = true;
        return this;
    }

    public @Nullable EntityType<?> getBase() { return base; }
    public @Nullable Identifier getModel() { return model; }
    public @Nullable Identifier getTexture() { return texture; }
    public @Nullable Identifier getBabyTexture() { return babyTexture; }
    public float getDrawScale() { return drawScale; }

    /** What vanilla clients are sent, or null for nothing. */
    public @Nullable EntityType<?> getVanillaFallback() {
        return fallback != null ? fallback : fallbackToBase ? base : null;
    }

    public boolean hasVanillaFallback() { return getVanillaFallback() != null; }

    /** Drawn from its own model, with no vanilla mob underneath. */
    public boolean hasOwnBody() { return base == null; }
}
