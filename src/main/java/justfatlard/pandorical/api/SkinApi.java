package justfatlard.pandorical.api;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * The skin a player is seen wearing, e.g. on an offline-mode server where profiles carry none.
 *
 * <p>Broadcast to everyone, including players who join later: callers name the subject, never the
 * audience. A client without the {@code "skins"} capability sees the profile's own skin.
 */
public interface SkinApi {
    /**
     * Dropped when the subject disconnects.
     *
     * @param png  a 64x64 skin image, as file bytes
     * @param slim the three-pixel-arm model, as opposed to the four-pixel default
     */
    void set(ServerPlayer subject, byte[] png, boolean slim);

    void clear(ServerPlayer subject);

    /**
     * Dress somebody who need not be online, e.g. for a player head. Kept for the life of the
     * server and sent to every later arrival, a few KB per subject on every join, so use it only
     * for skins known to be permanent.
     *
     * @param subject the UUID the subject's profile carries - on an offline server, their offline one
     */
    void set(MinecraftServer server, UUID subject, byte[] png, boolean slim);

    /** The subject's own skin again, for everyone, and nothing kept. */
    void clear(MinecraftServer server, UUID subject);
}
