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
 * Hides vanilla HUD elements at the server's request. Every suppressible element is wrapped at
 * client init, the only time Fabric's HUD registry is writable; the hidden set is per connection.
 */
public final class VanillaHudElementSuppressor {
    private VanillaHudElementSuppressor() {}

    private static final Set<Identifier> suppressed = ConcurrentHashMap.newKeySet();
    private static boolean installed = false;

    /** Chat, the player list, the sleep overlay and the demo timer are never hidden. */
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
        VanillaHudElements.BOSS_BAR,
        VanillaHudElements.SCOREBOARD,
        VanillaHudElements.OVERLAY_MESSAGE,
        VanillaHudElements.TITLE_AND_SUBTITLE,
        VanillaHudElements.SUBTITLES
    );

    /** Client init only. */
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
