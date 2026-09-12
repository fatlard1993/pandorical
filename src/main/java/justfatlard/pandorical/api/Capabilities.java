package justfatlard.pandorical.api;

import java.util.List;

/**
 * The capability strings of the Pandorical handshake, and the only ones there are.
 *
 * <p>A Pandorical client declares {@link #CLIENT}, and {@link PandoricalApi#hasCapability} answers
 * from that declaration: any other string answers false for every player, a misspelt one included.
 * The server announces {@link #SERVER} back, which adds what only a server offers.
 */
public final class Capabilities {
    private Capabilities() {}

    public static final String SCREENS = "screens";
    public static final String CONTENT = "content";
    public static final String HUD = "hud";
    public static final String CAMERA = "camera";
    public static final String STRUCTURES = "structures";
    public static final String ENTITY_OVERLAYS = "entity_overlays";
    public static final String CHEST_OVERLAYS = "chest_overlays";
    public static final String KEYBINDS = "keybinds";
    public static final String HUD_ELEMENTS = "hud_elements";
    public static final String SKINS = "skins";
    public static final String RENDER_POLICY = "render_policy";
    public static final String ANIMATIONS = "animations";
    public static final String MOUNT_POLICY = "mount_policy";

    /** Server only: the server has a mods menu to open. */
    public static final String SETTINGS = "settings";
    /** Server only: the server marks blocks. */
    public static final String BLOCK_MARKS = "block_marks";

    /** What a Pandorical client of this version declares. */
    public static final List<String> CLIENT = List.of(SCREENS, CONTENT, HUD, CAMERA, STRUCTURES, ENTITY_OVERLAYS,
        CHEST_OVERLAYS, KEYBINDS, HUD_ELEMENTS, SKINS, RENDER_POLICY, ANIMATIONS, MOUNT_POLICY);

    /** What a Pandorical server of this version announces. */
    public static final List<String> SERVER = List.of(SCREENS, CONTENT, CAMERA, HUD, STRUCTURES, ENTITY_OVERLAYS,
        CHEST_OVERLAYS, KEYBINDS, HUD_ELEMENTS, SKINS, RENDER_POLICY, ANIMATIONS, MOUNT_POLICY, SETTINGS, BLOCK_MARKS);
}
