package justfatlard.pandorical.api;

import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Nether portals that go back the way they came.
 *
 * <p>Vanilla sends a portal's traveller to whichever portal on the far side is nearest to where
 * they would arrive, so two portals built close together in one world share one in the other, and
 * the way back from a trip can come out at a different portal from the one gone in by. Paired, a
 * portal remembers the portal its first traveller came out of, both ways round, and every trip
 * through either goes to the other while both stand. The first trip through a portal, or one whose
 * partner has been broken, finds its way as vanilla does, and that trip is what pairs it. A trip
 * that comes out of a portal already paired with another remembers the way there, one way only:
 * the pair it arrived at is left as it was.
 *
 * <p>Off unless a mod asks for it, and ops can turn it either way from Pandorical's page of the mod
 * menu, which outranks what any mod asked for.
 */
public interface PortalApi {
    /**
     * Pair nether portals unless an op has said otherwise. For a mod whose portals make the
     * vanilla muddle worse - several portals within reach of each other being the point of it.
     */
    void pairNetherPortals(boolean byDefault);

    /**
     * Keep some portals out of pairing: never paired, never a partner. For portals a mod sends
     * somewhere itself, which would otherwise be remembered as where an ordinary portal goes.
     *
     * @param ours true for a portal block that belongs to the caller
     */
    void keepOutOfPairing(BiPredicate<ServerLevel, BlockPos> ours);
}
