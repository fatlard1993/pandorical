package justfatlard.pandorical.settings;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import justfatlard.pandorical.api.Capabilities;
import justfatlard.pandorical.api.PandoricalApi;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Settings by command, for a vanilla client that cannot show the screen. */
public final class SettingsCommand {
    private SettingsCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, SettingsRegistry settings) {
        PandoricalApi.commandHelp().describe("/pandorical mods",
            "Every mod on this server: what it is for, what it lets you change, and what it adds.");
        PandoricalApi.commandHelp().describe("/pandorical brief",
            "What every mod on this server is, in a line each. Shown once when you first join.");
        PandoricalApi.commandHelp().describe("/pandorical settings",
            "Your settings, for every mod that has any.");
        PandoricalApi.commandHelp().describe("/pandorical settings list",
            "The same as a list of lines, for a client that cannot draw the screen.");
        PandoricalApi.commandHelp().describe("/pandorical settings <mod> <key> <value>",
            "Change one setting by name, without the screen.");

        dispatcher.register(Commands.literal("pandorical")
            .then(Commands.literal("mods")
                .executes(context -> mods(context.getSource())))
            .then(Commands.literal("brief")
                .executes(context -> brief(context.getSource())))
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

    private static int brief(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!PandoricalApi.hasCapability(player, Capabilities.SCREENS)) {
            source.sendSuccess(() -> text("pandorical.settings.no_client"), false);
            return 0;
        }
        justfatlard.pandorical.brief.Brief.show(player);
        return 1;
    }

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

    /** The fallback text for a vanilla client, which has no Pandorical lang. */
    private static final Map<String, String> ENGLISH = readEnglish();

    private static Component text(String key, Object... args) {
        return Component.translatableWithFallback(key, ENGLISH.getOrDefault(key, key), args);
    }

    private static Map<String, String> readEnglish() {
        try (var in = SettingsCommand.class.getResourceAsStream("/assets/pandorical/lang/en_us.json")) {
            if (in == null) return Map.of();
            return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8),
                new TypeToken<Map<String, String>>() {}.getType());
        } catch (IOException | RuntimeException e) {
            return Map.of();
        }
    }
}
