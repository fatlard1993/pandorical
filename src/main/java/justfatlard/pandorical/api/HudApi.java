package justfatlard.pandorical.api;

import justfatlard.pandorical.protocol.ComponentUpdate;
import justfatlard.pandorical.protocol.ShowHudS2C;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public interface HudApi {
    /**
     * Show a HUD overlay for a player.
     * This is a no-op if the player lacks the {@code "hud"} capability (i.e. their client
     * does not support HUD rendering). The {@code overlay} id must match the id used in
     * subsequent {@link #update} and {@link #hide} calls. Use {@link HudBuilder} to construct
     * the {@link ShowHudS2C} payload.
     */
    void show(ServerPlayer player, ShowHudS2C overlay);

    /**
     * Send delta updates to a live HUD overlay.
     * Only the component entries present in {@code updates} are changed; all other components
     * in the overlay retain their current state (partial update, not a full replace).
     * The {@code overlayId} must match the id used in the corresponding {@link #show} call.
     */
    void update(ServerPlayer player, String overlayId, List<ComponentUpdate> updates);

    /**
     * Hide a HUD overlay for a player.
     * Instructs the client to stop rendering the overlay. Does not remove any server-side
     * state associated with the overlay — call {@link #show} again to restore it.
     */
    void hide(ServerPlayer player, String overlayId);
}
