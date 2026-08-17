package justfatlard.pandorical.api;

import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * API for server mods to draw particular chests with a different texture, e.g.
 * marking the ones a village generated with so a player can tell them from
 * their own.
 *
 * <p>The texture is a sprite base in the vanilla chests atlas: no extension, and
 * no {@code _left} / {@code _right} suffix, which the client appends itself for
 * each half of a double chest. That atlas is assembled from a directory source
 * covering every namespace, so shipping
 * {@code assets/<yourmod>/textures/entity/chest/<name>.png} is the whole of the
 * registration. All three files are needed: {@code <name>.png},
 * {@code <name>_left.png} and {@code <name>_right.png}.
 *
 * <p>Unlike {@link EntityOverlayApi}, these are addressed to one player rather
 * than broadcast, because whether a chest deserves marking can depend on who is
 * looking: the same chest may be a landlord's and a guest's.
 *
 * <p>Nothing is persisted and nothing is remembered across a reconnect. A mod
 * that wants marks to survive one must send them again on join, which is also
 * the only moment it can know which player it is talking to.
 *
 * <p>All calls are no-ops for players whose client lacks the
 * {@code "chest_overlays"} capability; vanilla clients are unaffected.
 */
public interface ChestOverlayApi {
    /**
     * Replace everything this player has marked with {@code texture}. Positions
     * marked with a different texture are left alone.
     *
     * <p>This is the call for a join: it states the whole truth in one message
     * rather than assuming what the client still remembers.
     */
    void replace(ServerPlayer player, Identifier texture, Collection<BlockPos> positions);

    /** Mark more positions with {@code texture}, leaving existing marks in place. */
    void add(ServerPlayer player, Identifier texture, Collection<BlockPos> positions);

    /** Unmark positions, whatever texture they were carrying. */
    void remove(ServerPlayer player, Collection<BlockPos> positions);
}
