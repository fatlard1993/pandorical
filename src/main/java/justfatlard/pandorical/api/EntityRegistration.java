package justfatlard.pandorical.api;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

/**
 * A new entity, drawn on Pandorical clients as a vanilla one it is built on.
 *
 * <p>The entity type is a real one, registered by the mod in the usual way, with its own class,
 * goals, attributes, spawns and loot. What a client cannot be handed is the class itself, so the
 * client stands in the {@link #base} for it: the base's class is what the client runs, at the
 * base's size, drawn by the base's renderer, and then dressed with whatever {@link #model} and
 * {@link #texture} say.
 *
 * <p><b>The one contract.</b> The server's class must extend the base's class and add no synced
 * data of its own, because the client decodes the entity's data by the base's layout. Anything
 * kept in fields, attributes, goals or save data is free; a new {@code EntityDataAccessor} is
 * not. Pandorical checks the first one of each type that loads and says so loudly if it breaks
 * this.
 *
 * <p><b>Vanilla clients</b> are not sent the entity at all unless {@link #vanillaFallback} is set,
 * in which case they are sent the base, which is the same animal undressed.
 */
public final class EntityRegistration {
    private @Nullable EntityType<?> base;
    private @Nullable Identifier model;
    private @Nullable Identifier texture;
    private @Nullable Identifier babyTexture;
    private float drawScale = 1F;
    private boolean vanillaFallback;

    /** The vanilla type the client stands in: its class, size and renderer. Required. */
    public EntityRegistration base(EntityType<?> base) {
        this.base = base;
        return this;
    }

    /**
     * Geometry for the whole type, as {@link EntityModelApi} reads it:
     * {@code assets/<namespace>/pandorical/entity_models/<path>.json}. Living entities only. One set
     * on a single entity through {@link EntityModelApi} wins over this.
     *
     * <p>It stands in for the baby's model too, so a baby wears {@link #texture} rather than
     * {@link #babyTexture}, and is drawn at its age scale.
     */
    public EntityRegistration model(Identifier model) {
        this.model = model;
        return this;
    }

    /** The skin, laid out for the base's model (or for {@link #model}, if one is given). */
    public EntityRegistration texture(Identifier texture) {
        this.texture = texture;
        return this;
    }

    /**
     * The skin while it is a baby. The game draws most babies with a model of their own that lays
     * its texture out differently, so the adult skin on a baby lands on the wrong boxes.
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

    /** Vanilla clients are sent the base, rather than nothing. */
    public EntityRegistration vanillaFallback() {
        this.vanillaFallback = true;
        return this;
    }

    public @Nullable EntityType<?> getBase() { return base; }
    public @Nullable Identifier getModel() { return model; }
    public @Nullable Identifier getTexture() { return texture; }
    public @Nullable Identifier getBabyTexture() { return babyTexture; }
    public float getDrawScale() { return drawScale; }
    public boolean hasVanillaFallback() { return vanillaFallback; }
}
