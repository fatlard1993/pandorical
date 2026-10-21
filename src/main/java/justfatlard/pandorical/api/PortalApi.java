package justfatlard.pandorical.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;

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
     * Whether portals are immersive when no op has chosen: a player steps through a nether portal
     * in half a second, and a Pandorical client draws the crossing as one swirl rather than a
     * loading screen. Off until a mod asks; the last call among mods wins, and an op's choice on
     * Pandorical's mod menu page outranks them all.
     */
    void immersivePortals(boolean byDefault);

    /**
     * Say where your own portals lead, so an immersive one shows it. Asked twice a second for the
     * nether portal nearest each player, before pairing is: answer with any block of the portal it
     * leads to, or null for a portal that is not yours. The view is laid out as vanilla carries a
     * traveller, sheet middle to sheet middle and turned a quarter between sheets facing different
     * ways, so a mod that turns travellers otherwise shows a far side they do not come out facing.
     */
    void lookThrough(BiFunction<ServerLevel, BlockPos, GlobalPos> farSide);

    /**
     * Keep some portals out of pairing, never paired and never a partner: for portals a mod sends
     * somewhere itself.
     *
     * @param ours true for a portal block that belongs to the caller
     */
    void keepOutOfPairing(BiPredicate<ServerLevel, BlockPos> ours);
}
