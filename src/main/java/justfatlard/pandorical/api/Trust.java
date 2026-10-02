package justfatlard.pandorical.api;

import java.util.Locale;

/**
 * What an op can allow or keep from a player, one at a time; see {@link TrustApi}.
 *
 * <p>Each has a server-wide default and a per-player choice that overrides it. PvP's default is the
 * game's own {@code pvp} gamerule; the others' are Pandorical's, all on until an op says otherwise.
 */
public enum Trust {
    /** Hurting other players, and being hurt by them: off for either player means neither way. */
    PVP("PvP", "Hurt other players, and be hurt by them"),
    /** Flint and steel, fire charges, lava buckets, and fire a mod sets on a player's behalf. */
    FIRE("Fire and lava", "Light fires and pour lava"),
    /** TNT, end crystals, beds and respawn anchors where they explode, and the wither. */
    EXPLOSIVES("Explosives", "Light TNT, place end crystals, set off beds and anchors, summon the wither"),
    /** Tamed animals and mounts of other players, villagers, and named creatures. */
    ANIMALS("Others' animals", "Hurt pets and mounts that are not theirs, villagers, and named creatures");

    public final String label;
    public final String description;

    Trust(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Trust byId(String id) {
        for (Trust trust : values()) if (trust.id().equals(id)) return trust;
        return null;
    }
}
