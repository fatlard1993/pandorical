package justfatlard.pandorical.api;

import net.minecraft.server.level.ServerPlayer;

/**
 * Key presses from Pandorical clients, with no client code of your own.
 *
 * <p>Clients register a fixed pool of eight rebindable keybinds at startup, the only time
 * Minecraft accepts them: category "Pandorical", "Pandorical Action 1..8", slot 1 on G, slot 2
 * on B, the rest unbound. {@link #register} claims a slot and names it, so the controls screen
 * shows the server's name on it.
 *
 * <p>Register during server-side mod initialisation, before any player connects: declarations
 * are pushed at the handshake. Clients without the {@code "keybinds"} capability send nothing.
 */
public interface KeybindApi {
    /**
     * Claims the slot whose default is {@code preferredDefaultKey}, else the lowest free one.
     *
     * @param id                  unique id for this keybind, namespaced, e.g. {@code "poopsmith:poop"}
     * @param preferredDefaultKey key code in the game's own InputConstants
     *                            table, NOT a GLFW code: use {@link #letter}.
     *                            0 asks for no key
     * @param displayName         name shown in the controls screen on claimed slots
     * @param handler             called on the server thread for each validated press
     */
    void register(String id, int preferredDefaultKey, String displayName, KeybindHandler handler);

    /**
     * Bind a registered keybind to its {@code preferredDefaultKey} even though no pool slot starts
     * on that key: once, on each client whose slot for it is unbound when it joins. The client
     * remembers by id, so a player's later change sticks. Pick a key vanilla does not use. Older
     * clients bind nothing.
     *
     * @param id a keybind already passed to {@link #register}
     */
    default void bindByDefault(String id) {}

    /**
     * The key code for a letter, in the table {@link #register} takes. The game numbers keys by
     * their USB usage, so A is 4 and B is 5, which is neither GLFW's nor ASCII's.
     */
    static int letter(char letter) {
        char upper = Character.toUpperCase(letter);
        if (upper < 'A' || upper > 'Z') throw new IllegalArgumentException("not a letter: " + letter);
        return 4 + (upper - 'A');
    }

    @FunctionalInterface
    interface KeybindHandler {
        void onPress(ServerPlayer player);

        /**
         * The key came back up. A release with no press before it (a key held across the join)
         * is delivered too, so read it as "not held now".
         */
        default void onRelease(ServerPlayer player) {}
    }
}
