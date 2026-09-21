package justfatlard.pandorical.api;

import java.util.List;

/** The capability strings of the Pandorical handshake: clients declare {@link #CLIENT}. */
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
    /** The client stands on and is carried by structures marked walkable. */
    public static final String WALKABLE_STRUCTURES = "walkable_structures";

    /**
     * What a server announces about itself, for its clients to read. These are not client
     * capabilities: {@link PandoricalApi#hasCapability} is false for them for every player, because
     * no client ever declares one.
     */
    public static final class Server {
        private Server() {}

        /** The server has a mods menu to open. */
        public static final String SETTINGS = "settings";
        /** The server marks blocks. */
        public static final String BLOCK_MARKS = "block_marks";
    }

    /** What a Pandorical client of this version declares. */
    public static final List<String> CLIENT = List.of(SCREENS, CONTENT, HUD, CAMERA, STRUCTURES, ENTITY_OVERLAYS,
        CHEST_OVERLAYS, KEYBINDS, HUD_ELEMENTS, SKINS, RENDER_POLICY, ANIMATIONS, MOUNT_POLICY, WALKABLE_STRUCTURES);

    /** What a Pandorical server of this version announces. */
    public static final List<String> SERVER = List.of(SCREENS, CONTENT, CAMERA, HUD, STRUCTURES, ENTITY_OVERLAYS,
        CHEST_OVERLAYS, KEYBINDS, HUD_ELEMENTS, SKINS, RENDER_POLICY, ANIMATIONS, MOUNT_POLICY, Server.SETTINGS,
        Server.BLOCK_MARKS, WALKABLE_STRUCTURES);

    /** True for a string only a server announces, which no client can ever declare. */
    static boolean isServerOnly(String capability) {
        return Server.SETTINGS.equals(capability) || Server.BLOCK_MARKS.equals(capability);
    }
}
