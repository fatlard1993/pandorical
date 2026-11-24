package justfatlard.pandorical.settings;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import justfatlard.pandorical.api.Capabilities;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.form.Forms;
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
        PandoricalApi.commandHelp().describe("/pandorical changes",
            "What changed since you were last here, or what each mod changed last.");
        PandoricalApi.commandHelp().describe("/pandorical actions",
            "This server's menus, settings and mod keys as one list.");
        PandoricalApi.commandHelp().describe("/pandorical notices",
            "Notices waiting for you, and their answers.");
        PandoricalApi.commandHelp().describe("/pandorical settings",
            "Your settings, for every mod that has any.");
        PandoricalApi.commandHelp().describe("/pandorical settings list",
            "The same as a list of lines in chat.");
        PandoricalApi.commandHelp().describe("/pandorical settings <mod> <key> <value>",
            "Change one setting by name, without the screen.");

        dispatcher.register(Commands.literal("pandorical")
            .then(Commands.literal("mods")
                .executes(context -> mods(context.getSource())))
            .then(Commands.literal("brief")
                .executes(context -> brief(context.getSource())))
            .then(Commands.literal("changes")
                .executes(context -> changes(context.getSource())))
            .then(Commands.literal("actions")
                .executes(context -> {
                    justfatlard.pandorical.actions.QuickActions.open(context.getSource().getPlayerOrException());
                    return 1;
                }))
            .then(Commands.literal("notices")
                .executes(context -> {
                    PandoricalApi.notices().open(context.getSource().getPlayerOrException());
                    return 1;
                }))
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
        if (source.getPlayer() == null) return list(source, settings);
        ServerPlayer player = source.getPlayerOrException();
        if (Forms.wanted(player)) {
            SettingsDialogs.open(player, settings);
            return 1;
        }
        if (!PandoricalApi.hasCapability(player, Capabilities.SCREENS)) {
            source.sendSuccess(() -> text("pandorical.settings.no_client"), false);
            return list(source, settings);
        }
        PandoricalApi.settings().open(player);
        return 1;
    }

    private static int brief(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        justfatlard.pandorical.brief.Brief.show(player);
        return 1;
    }

    private static int changes(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        justfatlard.pandorical.changelog.Changelog.show(player);
        return 1;
    }

    private static int mods(CommandSourceStack source) throws CommandSyntaxException {
        openMods(source.getPlayerOrException());
        return 1;
    }

    /** The mod menu: Pandorical's screen where the client draws it, a form where it can't. */
    public static void openMods(ServerPlayer player) {
        if (Forms.wanted(player)) {
            SettingsDialogs.mods(player, PandoricalApi.settingsImpl());
        } else if (PandoricalApi.hasCapability(player, Capabilities.SCREENS)) {
            PandoricalApi.settingsImpl().open(player, null);
        } else {
            for (ModCatalog.ModInfo mod : ModCatalog.all()) {
                String line = mod.name() + " " + mod.version() + (mod.description().isEmpty() ? "" : " - " + mod.description());
                player.sendSystemMessage(Component.literal(line));
            }
        }
    }

    /**
     * The player a setting is read and written through. From the server console or RCON there is
     * none, so server settings, which read only the player's server, are reached through a stand-in.
     */
    private static ServerPlayer actor(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player != null ? player : net.fabricmc.fabric.api.entity.FakePlayer.get(source.getServer().overworld());
    }

    private static int list(CommandSourceStack source, SettingsRegistry settings) throws CommandSyntaxException {
        boolean console = source.getPlayer() == null;
        ServerPlayer player = actor(source);
        if (settings.isEmpty()) {
            source.sendSuccess(() -> text("pandorical.settings.none"), false);
            return 0;
        }
        int count = 0;
        // The console and RCON show each message as it comes, with nothing between them, so there
        // the whole listing is one message of lines
        java.util.List<String> lines = new java.util.ArrayList<>();
        java.util.function.Consumer<String> say = line -> {
            if (console) lines.add(line);
            else source.sendSuccess(() -> Component.literal(line), false);
        };
        for (SettingsRegistry.GroupImpl group : settings.groups()) {
            if (console ? !group.server : group.server && !SettingsRegistry.isOp(player)) continue;
            String heading = switch (group.kind) {
                case PLAYER -> group.modName;
                case CLIENT -> group.modName + " (your client)";
                case SERVER -> group.modName + " (server, ops only)";
            };
            say.accept(heading);
            for (SettingsRegistry.SettingImpl<?> setting : group.settings) {
                boolean hidden = !setting.visible(player);
                // The console can set a hidden one, so it sees them, marked
                if (hidden && !console) continue;
                say.accept("  " + group.modId + " " + setting.key + " = " + setting.shown(player) + "   (" + setting.label() + ")"
                    + (hidden ? "   [does nothing on this server now]" : ""));
                count++;
            }
        }
        if (console) source.sendSuccess(() -> Component.literal(String.join("\n", lines)), false);
        return count;
    }

    private static <T> int set(CommandSourceStack source, SettingsRegistry settings, String mod, String key, String value)
            throws CommandSyntaxException {
        boolean console = source.getPlayer() == null;
        ServerPlayer player = actor(source);
        @SuppressWarnings("unchecked")
        SettingsRegistry.SettingImpl<T> setting = (SettingsRegistry.SettingImpl<T>) settings.find(mod, key);
        if (setting == null) {
            source.sendFailure(text("pandorical.settings.unknown", mod + " " + key));
            return 0;
        }
        if (console && !settings.groupIsServer(setting)) {
            source.sendFailure(text("pandorical.settings.console_server_only"));
            return 0;
        }
        if (!console && settings.groupIsServer(setting) && !SettingsRegistry.isOp(player)) {
            source.sendFailure(text("pandorical.settings.ops_only"));
            return 0;
        }
        // The console may set one that is hidden now, such as a choice that waits for another mod
        // to be removed: it is kept, and takes effect when the setting does
        boolean hidden = !setting.visible(player);
        if (hidden && !console) {
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
        source.sendSuccess(() -> hidden
            ? text("pandorical.settings.console_kept")
            : text("pandorical.settings.set", setting.label(), setting.shown(player)), false);
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
