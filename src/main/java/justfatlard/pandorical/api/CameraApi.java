package justfatlard.pandorical.api;

import net.minecraft.server.level.ServerPlayer;

public interface CameraApi {
    /** Resets when the player dismounts or disconnects. */
    void setDistance(ServerPlayer player, float distance);

    /** Force a perspective, e.g. {@code "third_person_back"}; null hands it back to the player. */
    void setPerspective(ServerPlayer player, String perspective);

    /**
     * Narrow the field of view, the way a spyglass does.
     *
     * @param factor how much to narrow by; 1.0 is normal, 0.35 is close in. Values above 1 widen.
     *               Pass 1.0 to release it.
     */
    void zoom(ServerPlayer player, float factor);

    void reset(ServerPlayer player);
}
