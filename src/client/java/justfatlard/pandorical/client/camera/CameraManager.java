package justfatlard.pandorical.client.camera;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.protocol.CameraHintS2C;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

public class CameraManager {
    private static float overrideDistance = -1;
    private static CameraType savedPerspective = null;
    private static boolean perspectiveForced = false;
    private static float zoomFactor = 1.0F;

    public static void handleHint(CameraHintS2C hint) {
        switch (hint.hintType()) {
            case "distance" -> {
                String distStr = hint.params().get("distance");
                if (distStr != null) {
                    try {
                        overrideDistance = Float.parseFloat(distStr);
                    } catch (NumberFormatException e) {
                        Pandorical.LOGGER.warn("Invalid camera distance value: '{}'", distStr);
                    }
                }
            }
            case "zoom" -> {
                String value = hint.params().get("factor");
                try {
                    zoomFactor = value == null ? 1.0F : Float.parseFloat(value);
                } catch (NumberFormatException e) {
                    Pandorical.LOGGER.warn("Invalid camera zoom factor: '{}'", value);
                    zoomFactor = 1.0F;
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

    /** 1.0 is no zoom. */
    public static float getZoomFactor() {
        return zoomFactor;
    }

    /** -1 when there is no override. */
    public static float getOverrideDistance() {
        return overrideDistance;
    }

    public static void reset() {
        zoomFactor = 1.0F;
        overrideDistance = -1;
        if (perspectiveForced && savedPerspective != null) {
            Minecraft.getInstance().options.setCameraType(savedPerspective);
        }
        perspectiveForced = false;
        savedPerspective = null;
    }

    public static void onDisconnect() {
        reset();
    }
}
