package justfatlard.pandorical.api;

import justfatlard.pandorical.protocol.ComponentUpdate;
import justfatlard.pandorical.protocol.ShowHudS2C;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;

public interface HudApi {
    /** No-op without the {@code "hud"} capability. Build the overlay with {@link HudBuilder}. */
    void show(ServerPlayer player, ShowHudS2C overlay);

    /**
     * Only the listed components and props change. Geometry, scale and rotation interpolate on the client, so
     * pushing them once a tick draws smoothly.
     */
    void update(ServerPlayer player, String overlayId, List<ComponentUpdate> updates);

    /** To bring it back, {@link #show} it again. */
    void hide(ServerPlayer player, String overlayId);

    /**
     * Ask this player's client to stop drawing these {@link VanillaHudElement}s. Replaces what
     * {@code ownerId} asked for before; an element stays hidden while any owner wants it hidden.
     *
     * <p>No-op without the {@code "hud_elements"} capability: those clients keep drawing
     * vanilla's version, so a replacement overlay must be legible over it or not be sent. Lasts
     * one connection: ask again each session.
     */
    void hideVanillaElements(ServerPlayer player, String ownerId, Collection<String> elementIds);

    /** Drop {@code ownerId}'s suppression request, restoring anything no other owner hides. */
    void restoreVanillaElements(ServerPlayer player, String ownerId);
}
