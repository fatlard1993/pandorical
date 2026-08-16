package justfatlard.pandorical.client.hud;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.protocol.SetVanillaHudElementsS2C;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lets a server stand its own Pandorical overlay in for a vanilla HUD element (the
 * hunger bar, say) by suppressing vanilla's.
 *
 * <p>Every suppressible element is wrapped once at client init, because Fabric's
 * registry is a startup-time structure while suppression is per connection: the
 * wrapper stays installed for the process lifetime and consults a mutable set each
 * frame. That set is cleared on disconnect, so a suppression never leaks into the
 * next world or server.
 */
public final class VanillaHudElementSuppressor {
    private VanillaHudElementSuppressor() {}

    private static final Set<Identifier> suppressed = ConcurrentHashMap.newKeySet();
    private static boolean installed = false;

    /**
     * Elements a server may suppress. Deliberately not every vanilla element: chat,
     * the player list, the sleep overlay and the demo timer stay untouchable so a
     * server cannot blind a player to the things they need to leave or communicate.
     */
    private static final List<Identifier> SUPPRESSIBLE = List.of(
        VanillaHudElements.MISC_OVERLAYS,
        VanillaHudElements.CROSSHAIR,
        VanillaHudElements.HOTBAR,
        VanillaHudElements.ARMOR_BAR,
        VanillaHudElements.HEALTH_BAR,
        VanillaHudElements.FOOD_BAR,
        VanillaHudElements.AIR_BAR,
        VanillaHudElements.MOUNT_HEALTH,
        VanillaHudElements.INFO_BAR,
        VanillaHudElements.EXPERIENCE_LEVEL,
        VanillaHudElements.HELD_ITEM_TOOLTIP,
        VanillaHudElements.MOB_EFFECTS,
        VanillaHudElements.SCOREBOARD,
        VanillaHudElements.OVERLAY_MESSAGE,
        VanillaHudElements.TITLE_AND_SUBTITLE,
        VanillaHudElements.SUBTITLES
    );

    /** Install the wrappers. Safe to call once, at client init. */
    public static void init() {
        if (installed) return;
        installed = true;
        for (Identifier id : SUPPRESSIBLE) {
            try {
                HudElementRegistry.replaceElement(id, original -> (extractor, delta) -> {
                    if (suppressed.contains(id)) return;
                    original.extractRenderState(extractor, delta);
                });
            } catch (Exception e) {
                // An element this client build doesn't have is not fatal: it just
                // stays unsuppressible.
                Pandorical.LOGGER.debug("Could not wrap vanilla HUD element {}: {}", id, e.getMessage());
            }
        }
    }

    public static void handle(SetVanillaHudElementsS2C payload) {
        suppressed.clear();
        for (String raw : payload.hiddenElements()) {
            Identifier id = Identifier.tryParse(raw);
            if (id == null) {
                Pandorical.LOGGER.warn("Server asked to hide unparseable HUD element id '{}'", raw);
                continue;
            }
            if (!SUPPRESSIBLE.contains(id)) {
                Pandorical.LOGGER.warn("Server asked to hide HUD element '{}', which Pandorical does not allow hiding", raw);
                continue;
            }
            suppressed.add(id);
        }
        Pandorical.LOGGER.debug("Suppressing {} vanilla HUD element(s): {}", suppressed.size(), suppressed);
    }

    public static void clear() {
        suppressed.clear();
    }
}
