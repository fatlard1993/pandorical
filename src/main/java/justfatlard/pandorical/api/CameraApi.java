package justfatlard.pandorical.api;

import net.minecraft.server.level.ServerPlayer;

/**
 * API for server mods to control client camera behavior.
 */
public interface CameraApi {
    /**
     * Set camera distance for a player (e.g., when riding a large entity).
     * Resets when player dismounts or disconnects.
     */
    void setDistance(ServerPlayer player, float distance);

    /**
     * Force a camera perspective (e.g., "third_person_back" when mounting ships).
     * Pass null to let the player control their own perspective.
     */
    void setPerspective(ServerPlayer player, String perspective);

    /**
     * Reset all camera hints for a player.
     */
    void reset(ServerPlayer player);
}
