package justfatlard.pandorical.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

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
     * Say where your own portals lead, so an immersive one shows it. Asked twice a second, for each
     * player, about the nether portal nearest them, before pairing is asked.
     */
    void lookThrough(LookThrough answer);

    @FunctionalInterface
    interface LookThrough {
        /**
         * Where this portal leads for this player, or null for a portal that is not yours.
         *
         * @param viewer the player who would see through it, for portals that lead each traveller
         *               somewhere of their own
         */
        FarEnd farEnd(ServerPlayer viewer, ServerLevel level, BlockPos portal);
    }

    /**
     * The portal a trip comes out of, and the face of it.
     *
     * <p>With no face, the view is laid out as vanilla carries a traveller: sheet middle to sheet
     * middle, turned a quarter between sheets facing different ways, so each side of the near sheet
     * shows the far side a traveller walking through it would come out on. With a face, every trip
     * comes out of that one, and both sides of the near sheet show what stands in front of it,
     * turned so that walking in is walking out of it.
     *
     * @param portal any block of the far portal's sheet
     * @param face   the side of that sheet every traveller comes out of, or null for vanilla's
     */
    record FarEnd(GlobalPos portal, Direction face) {
        public static FarEnd of(GlobalPos portal) {
            return new FarEnd(portal, null);
        }

        public static FarEnd outOf(GlobalPos portal, Direction face) {
            return new FarEnd(portal, face);
        }
    }

    /**
     * Keep some portals out of pairing, never paired and never a partner: for portals a mod sends
     * somewhere itself.
     *
     * @param ours true for a portal block that belongs to the caller
     */
    void keepOutOfPairing(BiPredicate<ServerLevel, BlockPos> ours);
}
