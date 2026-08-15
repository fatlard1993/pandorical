package justfatlard.pandorical.api;

import net.minecraft.server.level.ServerPlayer;

/**
 * API for server mods to receive keybind presses from Pandorical clients
 * without shipping any client code of their own.
 *
 * <p>Pandorical clients register a fixed pool of rebindable keybinds at
 * normal client startup (category "Pandorical", slots "Pandorical Action
 * 1..8"; slot 1 defaults to G, the rest start unbound). This pool exists
 * because Minecraft's options system only accepts keybind registration during
 * client startup: a dynamically added KeyMapping would neither persist its
 * rebinds to options.txt nor survive Fabric's registration-before-options
 * guard. Server mods therefore claim pool slots instead of registering real
 * per-mod keys.
 *
 * <p>{@link #register} claims a slot (preferring one whose pool default
 * matches {@code preferredDefaultKey}, else the lowest free slot) and names
 * it: the display name is shipped to clients as a lang entry in the synced
 * pandorical asset pack, so the controls screen shows the server's name on
 * the claimed slot. Presses arrive as a generic slot index and are dispatched
 * to the registered handler on the server thread.
 *
 * <p>Register during server-side mod initialisation, before any players
 * connect (same constraint as content registration): declarations and lang
 * are pushed per player at handshake time. Clients without the
 * {@code "keybinds"} capability (older Pandorical, vanilla) never receive or
 * send any of this.
 */
public interface KeybindApi {
    /**
     * Claim a pooled keybind slot.
     *
     * @param id                  unique id for this keybind, namespaced, e.g. {@code "poopsmith:poop"}
     * @param preferredDefaultKey key code in the game's own InputConstants
     *                            table, NOT a GLFW code (KEY_G is 10 on this
     *                            snapshot generation; verify against the
     *                            client jar when in doubt). Honored only when
     *                            a free pool slot has that default, since
     *                            pool defaults are fixed client-side
     * @param displayName         name shown in the controls screen on claimed slots
     * @param handler             called on the server thread for each validated press
     */
    void register(String id, int preferredDefaultKey, String displayName, KeybindHandler handler);

    @FunctionalInterface
    interface KeybindHandler {
        void onPress(ServerPlayer player);
    }
}
