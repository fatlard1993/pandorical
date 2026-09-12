package justfatlard.pandorical.api;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/**
 * Particular chests drawn with a different texture, per player.
 *
 * <p>The texture is a sprite id in the vanilla chests atlas, with no extension and no
 * {@code _left}/{@code _right} suffix: the client appends those for each half of a double
 * chest. Shipping {@code assets/<yourmod>/textures/entity/chest/<name>.png},
 * {@code <name>_left.png} and {@code <name>_right.png} is the whole registration.
 *
 * <p><b>The id keeps the atlas's directory prefix:</b> {@code <yourmod>:entity/chest/<name>},
 * not {@code <yourmod>:<name>}. A bare name draws missing-texture magenta and nothing else
 * complains. Vanilla's are the same, e.g. {@code minecraft:entity/chest/christmas}.
 *
 * <p>Nothing is persisted or kept across a reconnect: send marks again each session, from
 * {@link PandoricalApi#onPlayerReady}. Players without the {@code "chest_overlays"} capability
 * are sent nothing.
 */
public interface ChestOverlayApi {
    /**
     * Replace everything this player has marked with {@code texture}. Positions
     * marked with a different texture are left alone.
     */
    void replace(ServerPlayer player, Identifier texture, Collection<BlockPos> positions);

    void add(ServerPlayer player, Identifier texture, Collection<BlockPos> positions);

    /** Whatever texture they were carrying. */
    void remove(ServerPlayer player, Collection<BlockPos> positions);
}
