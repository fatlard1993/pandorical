package justfatlard.pandorical.api;

import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.function.BiFunction;

/**
 * A button on a mod's page of the mod menu, beside its name, that runs a command as the player.
 *
 * <p>For a mod that has something to offer about other mods: a demo of each, a guide, a shop
 * page. The page belongs to the mod it describes, so the button is asked for every time a page is
 * laid out, for the player looking at it, and a mod with nothing to say about that page answers
 * null. Pressing it closes the mod menu and runs the command exactly as if the player had typed it,
 * so the command's own permission checks still decide.
 *
 * <p>The command is looked up again on the server when the button is pressed, never taken from the
 * client, so a page cannot be talked into running something it never showed.
 *
 * <p>Call during server-side mod initialisation.
 */
public interface ModPageApi {

    /**
     * @param label   a word or two: {@code "Play demo"}
     * @param tooltip one short line, or empty
     * @param command without the leading slash, as the player would type it
     */
    record Button(String label, String tooltip, String command) {}

    /** @param forMod the player looking, and the id of the mod whose page it is; null for no button */
    void button(BiFunction<ServerPlayer, String, @Nullable Button> forMod);
}
