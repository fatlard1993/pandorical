package justfatlard.pandorical.settings;

import justfatlard.pandorical.api.Capabilities;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import justfatlard.pandorical.api.PandoricalApi;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The settings for anyone who cannot see the screen.
 *
 * <p>{@code /pandorical settings} opens the screen on a Pandorical client. {@code list} says what
 * every setting is, and {@code <mod> <key> <value>} changes one, which is what a vanilla client
 * has instead of a screen.
 */
public final class SettingsCommand {
    private SettingsCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, SettingsRegistry settings) {
        dispatcher.register(Commands.literal("pandorical")
            .then(Commands.literal("mods")
                .executes(context -> mods(context.getSource())))
            .then(Commands.literal("settings")
                .executes(context -> open(context.getSource(), settings))
                .then(Commands.literal("list").executes(context -> list(context.getSource(), settings)))
                .then(Commands.argument("mod", StringArgumentType.word())
                    .then(Commands.argument("key", StringArgumentType.word())
                        .then(Commands.argument("value", StringArgumentType.greedyString())
                            .executes(context -> set(context.getSource(), settings,
                                StringArgumentType.getString(context, "mod"),
                                StringArgumentType.getString(context, "key"),
                                StringArgumentType.getString(context, "value"))))))));
    }

    private static int open(CommandSourceStack source, SettingsRegistry settings) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!PandoricalApi.hasCapability(player, Capabilities.SCREENS)) {
            source.sendSuccess(() -> text("pandorical.settings.no_client"), false);
            return list(source, settings);
        }
        PandoricalApi.settings().open(player);
        return 1;
    }

    /** The mod menu on a Pandorical client; the list of mods, one line each, on any other. */
    private static int mods(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (PandoricalApi.hasCapability(player, Capabilities.SCREENS)) {
            PandoricalApi.settingsImpl().open(player, null);
            return 1;
        }
        for (ModCatalog.ModInfo mod : ModCatalog.all()) {
            String line = mod.name() + " " + mod.version() + (mod.description().isEmpty() ? "" : " - " + mod.description());
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return ModCatalog.all().size();
    }

    private static int list(CommandSourceStack source, SettingsRegistry settings) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (settings.isEmpty()) {
            source.sendSuccess(() -> text("pandorical.settings.none"), false);
            return 0;
        }
        int count = 0;
        for (SettingsRegistry.GroupImpl group : settings.groups()) {
            if (group.server && !SettingsRegistry.isOp(player)) continue;
            String heading = switch (group.kind) {
                case PLAYER -> group.modName;
                case CLIENT -> group.modName + " (your client)";
                case SERVER -> group.modName + " (server, ops only)";
            };
            source.sendSuccess(() -> Component.literal(heading), false);
            for (SettingsRegistry.SettingImpl<?> setting : group.settings) {
                if (!setting.visible(player)) continue;
                String line = "  " + group.modId + " " + setting.key + " = " + setting.shown(player) + "   (" + setting.label() + ")";
                source.sendSuccess(() -> Component.literal(line), false);
                count++;
            }
        }
        return count;
    }

    private static <T> int set(CommandSourceStack source, SettingsRegistry settings, String mod, String key, String value)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        @SuppressWarnings("unchecked")
        SettingsRegistry.SettingImpl<T> setting = (SettingsRegistry.SettingImpl<T>) settings.find(mod, key);
        if (setting == null) {
            source.sendFailure(text("pandorical.settings.unknown", mod + " " + key));
            return 0;
        }
        if (settings.groupIsServer(setting) && !SettingsRegistry.isOp(player)) {
            source.sendFailure(text("pandorical.settings.ops_only"));
            return 0;
        }
        if (!setting.visible(player)) {
            source.sendFailure(text("pandorical.settings.hidden"));
            return 0;
        }
        T parsed = setting.parse(value.trim());
        if (parsed == null) {
            String accepts = setting.accepts();
            source.sendFailure(accepts.isEmpty()
                ? text("pandorical.settings.bad_value", value)
                : text("pandorical.settings.bad_value_takes", value, setting.label(), accepts));
            return 0;
        }
        setting.set(player, parsed);
        source.sendSuccess(() -> text("pandorical.settings.set", setting.label(), setting.shown(player)), false);
        return 1;
    }

    /** Pandorical's own English, read out of this jar, for the games that do not have it. */
    private static final java.util.Map<String, String> ENGLISH = readEnglish();

    /**
     * Text a player on any game can read: the key for one with Pandorical's language, and the
     * English for a vanilla game, which is who this command is for as often as not.
     */
    private static Component text(String key, Object... args) {
        return Component.translatableWithFallback(key, ENGLISH.getOrDefault(key, key), args);
    }

    private static java.util.Map<String, String> readEnglish() {
        try (var in = SettingsCommand.class.getResourceAsStream("/assets/pandorical/lang/en_us.json")) {
            if (in == null) return java.util.Map.of();
            return new com.google.gson.Gson().fromJson(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8),
                new com.google.gson.reflect.TypeToken<java.util.Map<String, String>>() {}.getType());
        } catch (java.io.IOException | RuntimeException e) {
            return java.util.Map.of();
        }
    }
}
