package justfatlard.pandorical.client.camera;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.api.NotUnderstood;
import justfatlard.pandorical.client.ClientNotices;
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
                // Empty is the server handing the view back, which is what setPerspective(null) sends.
                if (mode == null || mode.isEmpty()) {
                    releasePerspective();
                    break;
                }
                CameraType target = switch (mode) {
                    case "third_person_back" -> CameraType.THIRD_PERSON_BACK;
                    case "third_person_front" -> CameraType.THIRD_PERSON_FRONT;
                    case "first_person" -> CameraType.FIRST_PERSON;
                    default -> null;
                };
                // An unknown mode leaves the view alone rather than holding a perspective nobody set.
                if (target == null) {
                    ClientNotices.report(NotUnderstood.PERSPECTIVE, mode);
                    break;
                }
                Minecraft client = Minecraft.getInstance();
                if (!perspectiveForced) {
                    savedPerspective = client.options.getCameraType();
                }
                perspectiveForced = true;
                if (client.options.getCameraType() != target) {
                    client.options.setCameraType(target);
                }
            }
            case "reset" -> reset();
            default -> ClientNotices.report(NotUnderstood.CAMERA_HINT, hint.hintType());
        }
    }

    /** Hands the view back to the player, keeping whatever they were using before. */
    private static void releasePerspective() {
        if (perspectiveForced && savedPerspective != null) {
            Minecraft.getInstance().options.setCameraType(savedPerspective);
        }
        perspectiveForced = false;
        savedPerspective = null;
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
        releasePerspective();
    }

    public static void onDisconnect() {
        reset();
    }
}
