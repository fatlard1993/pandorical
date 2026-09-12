package justfatlard.pandorical.api;

import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Nether portals that go back the way they came.
 *
 * <p>Vanilla sends a traveller to the far-side portal nearest where they would arrive, so nearby
 * portals share a partner. Paired, a portal remembers the portal its first traveller came out of,
 * both ways round, and every trip through either goes to the other while both stand. The first
 * trip through a portal, or one whose partner is broken, goes as vanilla does and pairs it. A trip
 * that comes out of an already-paired portal pairs one way only, leaving that pair as it was.
 */
public interface PortalApi {
    /**
     * Whether portals pair when no op has chosen. Off until a mod asks; the last call among mods
     * wins, and an op's choice on Pandorical's mod menu page outranks them all.
     */
    void pairNetherPortals(boolean byDefault);

    /**
     * Keep some portals out of pairing, never paired and never a partner: for portals a mod sends
     * somewhere itself.
     *
     * @param ours true for a portal block that belongs to the caller
     */
    void keepOutOfPairing(BiPredicate<ServerLevel, BlockPos> ours);
}
