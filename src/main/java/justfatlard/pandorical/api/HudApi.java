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
     *
     * <p>Geometry (position/size) and, on sprite/text, scale/rotation can be pushed the same way
     * as any other prop; see {@link ComponentType#PROP_X}/{@code PROP_Y}/{@code PROP_WIDTH}/
     * {@code PROP_HEIGHT}/{@code PROP_SCALE}/{@code PROP_ROTATION}, or build the update with
     * {@link ComponentUpdateBuilder} for convenience. The client smoothly interpolates towards
     * each new value rather than snapping; no special handling needed on the server side beyond
     * calling this as often as the value changes (server-tick-rate calls, e.g. once/tick, render
     * smoothly).
     */
    void update(ServerPlayer player, String overlayId, List<ComponentUpdate> updates);

    /**
     * Hide a HUD overlay for a player.
     * Instructs the client to stop rendering the overlay. Does not remove any server-side
     * state associated with the overlay; call {@link #show} again to restore it.
     */
    void hide(ServerPlayer player, String overlayId);

    /**
     * Ask this player's client to stop drawing the given vanilla HUD elements (see
     * {@link VanillaHudElement}), so a Pandorical overlay can stand in for one. Replaces
     * whatever {@code ownerId} previously asked for; other mods' requests are unaffected,
     * and an element stays hidden while any owner still wants it hidden.
     *
     * <p>No-op without the {@code "hud_elements"} capability: those clients keep drawing
     * vanilla's version, so a replacement overlay must still be legible over it, or the
     * mod should skip pushing the overlay to them. Suppression is per connection and is
     * not restored automatically after a rejoin: re-request it when the player rejoins,
     * exactly like entity overlays.
     */
    void hideVanillaElements(ServerPlayer player, String ownerId, java.util.Collection<String> elementIds);

    /** Drop {@code ownerId}'s suppression request, restoring anything no other owner hides. */
    void restoreVanillaElements(ServerPlayer player, String ownerId);
}
