package justfatlard.pandorical.api;

/**
 * A line of explanation for a command, shown under it in the mods screen.
 *
 * <p>Nothing in the game records what a command is for. Brigadier knows a command's shape - its
 * words, its arguments, who may run it - and nothing at all about why anybody would. So the mods
 * screen can list a mod's commands exactly and still leave a player none the wiser, which is what
 * this is for.
 *
 * <p>Say it in a few words and in the second person: the line sits under the command in a list of
 * them, and a paragraph there is a paragraph nobody reads.
 *
 * <p>Call during server-side mod initialisation.
 */
public interface CommandHelpApi {

    /**
     * @param command     the command as it is written, with or without the leading slash, and with
     *                    argument names where it takes them: {@code "/tpme place <name>"}. A
     *                    command with no line of its own falls back to the longest one that starts
     *                    it, so describing {@code "/pvp"} covers every {@code /pvp} subcommand
     *                    that has nothing better.
     * @param description one short line
     */
    void describe(String command, String description);
}
