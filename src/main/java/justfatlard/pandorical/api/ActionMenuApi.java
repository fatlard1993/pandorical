package justfatlard.pandorical.api;

import java.util.List;

/**
 * Buttons and menus a server offers a player the first time they join.
 *
 * <p>Action menus are the player's own grids, kept on their machine and edited from the mods menu.
 * Until now every one of them had to be built by hand, button by button, by somebody who already
 * knew what the server offered - which is exactly the person who does not need a menu. This is how
 * a mod says what belongs on one.
 *
 * <p>Everything declared here is also listed in the editor's "Add what this server offers", for
 * the life of the connection, so a player can put one into a menu of their own long after the
 * first join - which makes a promoted button worth declaring even for a mod that wants no menu.
 *
 * <p>Offered once each and then never again. The client records the id of every menu it has been
 * given, so a menu the player deletes stays deleted and one they rearrange stays rearranged. A
 * server cannot rewrite a player's menus from here; it can only suggest one they have never seen.
 *
 * <p>Call during server-side mod initialisation, before any player connects: suggestions are
 * assembled and pushed at the handshake, like keybind declarations.
 */
public interface ActionMenuApi {

    /**
     * @param icon       an item id drawn on the button, e.g. {@code minecraft:firework_rocket}
     * @param label      the word under it; keep it to a word or two
     * @param command    what it runs, with or without the leading slash; empty for a key button
     * @param keyMapping the key it presses, e.g. {@code key.advancements}; empty for a command
     */
    record Button(String icon, String label, String command, String keyMapping) {

        /** A button that runs a command, as if the player had typed it. */
        public static Button runs(String icon, String label, String command) {
            return new Button(icon, label, command, "");
        }

        /**
         * A button that presses a key, by its mapping name.
         *
         * <p>Only worth it for a key that opens something. A key the game reads while it is held -
         * the player list, sneak, sprint - is pressed for a couple of ticks and let go, which on
         * those reads as a flicker rather than an action.
         */
        public static Button presses(String icon, String label, String keyMapping) {
            return new Button(icon, label, "", keyMapping);
        }
    }

    /**
     * A button on the server's own menu, beside the ones every claimed keybind already gets.
     *
     * <p>For a command worth reaching for without typing. A command nobody would bind a key to is
     * a command nobody wants on a button either.
     */
    void suggestButton(Button button);

    /**
     * A menu of this mod's own, for when a mod has more to offer than one button.
     *
     * @param id      how the client remembers having been offered this; namespace it, and never
     *                change it, or every player is offered the menu again as though it were new
     * @param name    what it is called when it arrives
     * @param buttons in the order they should sit in the grid, at most 64
     */
    void suggestMenu(String id, String name, List<Button> buttons);

    /**
     * Put one of this mod's keybinds on the server's menu.
     *
     * <p>Only worth doing for a key somebody might not have bound, or might not remember they
     * have: an action menu is for the corners of the game, and a key you press constantly is
     * already faster than any menu. Claiming a keybind does not put it here; this does.
     *
     * @param keybindId the id passed to {@link KeybindApi#register}
     * @param icon      an item id drawn on the button
     */
    void promoteKeybind(String keybindId, String icon);
}
