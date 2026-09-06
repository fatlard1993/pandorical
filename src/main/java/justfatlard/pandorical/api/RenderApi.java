package justfatlard.pandorical.api;

/**
 * API for server mods to ask clients to render their content differently.
 *
 * <p>Server-wide rather than per-player: these describe the world, not the person looking at it.
 * A policy set here reaches everyone connected and everyone who connects afterwards, and lapses
 * when they leave.
 *
 * <p>Additive only. A policy can switch something on for a client that left it off; it cannot
 * switch anything off. A server is entitled to say what its own content needs and not to overrule
 * what a player wanted.
 */
public interface RenderApi {
    /**
     * Ask clients to stop drawing leaf faces buried inside a canopy.
     *
     * <p>For servers whose trees are much larger than vanilla's, where the interior of a crown is
     * hundreds of faces nobody can see. The outermost layer still renders normally, so this is a
     * cost change rather than a look change.
     */
    void cullLeaves(boolean enforce);
}
