package justfatlard.pandorical.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Pictures of what a player sees, taken by their own client: the world as their screen shows it,
 * with no HUD, hand or crosshair, the way F1 and F2 would take it.
 *
 * <p>The pixels come from the player's machine, so they are what that client chose to send: a
 * modified client can send any picture of the right size. Treat a capture as the player's own
 * upload, not as evidence of the world.
 *
 * <p>Not yet stable: it may still change shape.
 */
public interface CaptureApi {
    /** Largest side a capture may be asked for. */
    int MOST_SIDE = 1024;

    /** Whether this player's client takes captures; false for clients older than 15.15. */
    boolean canCapture(ServerPlayer player);

    /**
     * Ask the player's client for a square picture of its view, cropped from the middle of the
     * window and scaled to {@code side} pixels.
     *
     * <p>Completes on the server thread: with the picture, or empty if the client cannot or will
     * not take one (a screen is open over the world), leaves, sends something that is not a
     * {@code side} square PNG, or has not answered within ten seconds. One capture at a time per
     * player; asking again before the first completes is answered empty at once.
     *
     * @throws IllegalArgumentException if {@code side} is not 1 to {@link #MOST_SIDE}
     */
    CompletableFuture<Optional<Capture>> request(ServerPlayer player, int side);

    /** Whether this player's client takes captures from a viewpoint; false for clients older than 15.22. */
    boolean canCaptureFrom(ServerPlayer player);

    /**
     * Ask the player's client for a square picture of the world as seen from {@code eye}, looking
     * along {@code yRot} and {@code xRot} as an entity's rotation does, at a 70 degree field of
     * view. The client draws it off screen, so the player sees nothing of it and an open screen
     * does not stop it, and the player's own body is in the picture if it stands in view. The
     * client can draw only what it has loaded, so ask a player with {@code eye}'s chunk in view.
     *
     * <p>Completes as {@link #request(ServerPlayer, int)} does, and shares its one capture at a
     * time per player.
     *
     * @throws IllegalArgumentException if {@code side} is not 1 to {@link #MOST_SIDE}
     */
    CompletableFuture<Optional<Capture>> request(ServerPlayer player, int side, Vec3 eye, float yRot, float xRot);
}
