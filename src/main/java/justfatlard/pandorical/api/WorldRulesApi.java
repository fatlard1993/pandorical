package justfatlard.pandorical.api;

/**
 * Small rules of the world, each off unless a mod asks for it, and each one ops can turn from
 * Pandorical's page of the mod menu, which outranks what any mod asked for. The last call wins among
 * mods. All are the server's alone: a vanilla client sees the same world.
 */
public interface WorldRulesApi {
    /**
     * Whether rain brings crops on faster, when no op has chosen: a crop under open sky in the rain gets
     * extra random ticks, on the terms it already grows by, so light and moisture still decide. What
     * counts as a crop is the block tag {@code #pandorical:rain_grown}, which a mod adds to with a tag
     * file and no dependency.
     */
    void rainGrowth(boolean byDefault);

    /**
     * Whether a mob that dies with no player's hand in it, to a fall, lava, a golem or a trapdoor, drops a
     * share of the XP a player's kill would, when no op has chosen. Deaths vanilla already pays for are
     * left to vanilla.
     */
    void unattendedKillXp(boolean byDefault);
}
