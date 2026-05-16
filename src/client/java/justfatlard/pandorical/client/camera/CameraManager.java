package justfatlard.pandorical.client.camera;

import justfatlard.pandorical.protocol.CameraHintS2C;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

/**
 * Client-side camera hint manager.
 * Applies server-sent camera distance and perspective overrides.
 */
public class CameraManager {
    // All fields accessed only on the render thread via client.execute()
    private static float overrideDistance = -1; // -1 = no override
    private static CameraType savedPerspective = null;
    private static boolean perspectiveForced = false;

    /**
     * Handle a camera hint from the server.
     */
    public static void handleHint(CameraHintS2C hint) {
        switch (hint.hintType()) {
            case "distance" -> {
                String distStr = hint.params().get("distance");
                if (distStr != null) {
                    try {
                        overrideDistance = Float.parseFloat(distStr);
                    } catch (NumberFormatException e) {
                        justfatlard.pandorical.Pandorical.LOGGER.warn("Invalid camera distance value: '{}'", distStr);
                    }
                }
            }
            case "perspective" -> {
                String mode = hint.params().get("mode");
                if (mode != null) {
                    Minecraft client = Minecraft.getInstance();
                    if (!perspectiveForced) {
                        savedPerspective = client.options.getCameraType();
                    }
                    perspectiveForced = true;
                    CameraType target = switch (mode) {
                        case "third_person_back" -> CameraType.THIRD_PERSON_BACK;
                        case "third_person_front" -> CameraType.THIRD_PERSON_FRONT;
                        case "first_person" -> CameraType.FIRST_PERSON;
                        default -> null;
                    };
                    if (target != null && client.options.getCameraType() != target) {
                        client.options.setCameraType(target);
                    }
                }
            }
            case "reset" -> reset();
        }
    }

    /**
     * Called by CameraMixin to get the overridden camera distance.
     * Returns -1 if no override is active.
     */
    public static float getOverrideDistance() {
        return overrideDistance;
    }

    /**
     * Reset all camera overrides.
     */
    public static void reset() {
        overrideDistance = -1;
        if (perspectiveForced && savedPerspective != null) {
            Minecraft.getInstance().options.setCameraType(savedPerspective);
        }
        perspectiveForced = false;
        savedPerspective = null;
    }

    /**
     * Called on disconnect to clean up state.
     */
    public static void onDisconnect() {
        reset();
    }
}
