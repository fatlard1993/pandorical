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
     * Narrow the field of view, the way a spyglass does.
     *
     * <p>For looking closely at something in the world rather than opening a picture of it: a lock
     * being picked, a mechanism being read. The player keeps their own camera and their own place
     * in the world - this only changes how much of it fits on the screen, so what they are looking
     * at is still the thing itself and not an illustration of it.
     *
     * @param factor how much to narrow by; 1.0 is normal, 0.35 is close in. Values above 1 widen.
     *               Pass 1.0 to release it.
     */
    void zoom(ServerPlayer player, float factor);

    /**
     * Reset all camera hints for a player.
     */
    void reset(ServerPlayer player);
}
