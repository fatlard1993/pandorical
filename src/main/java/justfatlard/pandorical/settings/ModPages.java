package justfatlard.pandorical.settings;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.api.ModPageApi;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;

public final class ModPages implements ModPageApi {
    public static final ModPages INSTANCE = new ModPages();

    private final List<BiFunction<ServerPlayer, String, @Nullable Button>> providers = new CopyOnWriteArrayList<>();

    private ModPages() {}

    @Override
    public void button(BiFunction<ServerPlayer, String, @Nullable Button> forMod) {
        providers.add(forMod);
    }

    /** Index by index, so a press names the provider that drew it; null where one offered nothing. */
    List<@Nullable Button> buttonsFor(ServerPlayer player, String modId) {
        List<@Nullable Button> out = new java.util.ArrayList<>(providers.size());
        for (var provider : providers) out.add(ask(provider, player, modId));
        return out;
    }

    @Nullable Button buttonFor(ServerPlayer player, String modId, int index) {
        if (index < 0 || index >= providers.size()) return null;
        return ask(providers.get(index), player, modId);
    }

    /** One mod's broken provider costs that mod its button, not everyone the mod menu. */
    private static @Nullable Button ask(BiFunction<ServerPlayer, String, @Nullable Button> provider, ServerPlayer player, String modId) {
        try {
            return provider.apply(player, modId);
        } catch (RuntimeException e) {
            Pandorical.LOGGER.error("A mod page button for {} failed", modId, e);
            return null;
        }
    }

    static void run(ServerPlayer player, Button button) {
        String command = button.command().startsWith("/") ? button.command().substring(1) : button.command();
        player.level().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
    }
}
