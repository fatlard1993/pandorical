package justfatlard.pandorical.api;

import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * API for server mods to decide what skin a player is seen wearing.
 *
 * <p>For servers where the usual answer is unavailable - an offline-mode server, where a player's
 * profile carries no textures at all and everyone is Steve.
 *
 * <p>Broadcast, not per-player: a skin is a fact about the person wearing it, so it goes to
 * everyone who might look at them, including people who have not joined yet. Callers name the
 * subject and never the audience.
 *
 * <p>All calls are no-ops for players whose client lacks the {@code "skins"} capability, and such a
 * client simply sees the profile's own skin as before.
 */
public interface SkinApi {
    /**
     * Dress a player in this image for everyone who can see them.
     *
     * @param png  a 64x64 skin image, as file bytes
     * @param slim the three-pixel-arm model, as opposed to the four-pixel default
     */
    void set(ServerPlayer subject, byte[] png, boolean slim);

    /** Give a player their own skin back. */
    void clear(ServerPlayer subject);

    /**
     * Dress somebody who need not be online.
     *
     * <p>A head on a wall belongs to a player who may have left, and a face that reaches only the
     * clients who saw them join leaves it wearing Steve for everyone after. Unlike the
     * {@link ServerPlayer} form, this is kept for the life of the server rather than dropped when
     * the subject disconnects, and every later arrival receives it too. Callers pay for that in
     * a few KB per subject on every join, so it is for skins that are known to be permanent.
     *
     * @param subject the UUID the subject's profile carries - on an offline server, their offline one
     */
    void set(MinecraftServer server, UUID subject, byte[] png, boolean slim);

    /** Undo the above: the subject's own skin, for everyone, and nothing kept. */
    void clear(MinecraftServer server, UUID subject);
}
