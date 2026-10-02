package justfatlard.pandorical.trust;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import justfatlard.pandorical.api.Capabilities;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.Trust;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.Arrays;
import java.util.UUID;

/**
 * {@code /pandorical trust}: the page, or the same choices by name for a console or a vanilla
 * client. Ops only. A player is named as a word rather than an entity, so one who is offline can be
 * set too; the names offered are everyone the server has seen.
 */
public final class TrustCommand {
	private TrustCommand() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		PandoricalApi.commandHelp().describe("/pandorical trust",
			"Ops: who may fight other players, use fire and lava, use explosives, or hurt others' animals.");
		PandoricalApi.commandHelp().describe("/pandorical trust <player> <pvp|fire|explosives|animals> <on|off|default>",
			"Ops: set one player's trust by name, online or not.");
		PandoricalApi.commandHelp().describe("/pandorical trust everyone <pvp|fire|explosives|animals> <on|off>",
			"Ops: the default for anyone not set by name. PvP's is the pvp gamerule.");

		dispatcher.register(Commands.literal("pandorical")
			.then(Commands.literal("trust")
				.requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
				.executes(context -> show(context.getSource()))
				.then(Commands.literal("everyone")
					.then(Commands.argument("what", StringArgumentType.word()).suggests(TrustCommand::switches)
						.then(Commands.argument("value", StringArgumentType.word())
							.suggests((c, b) -> SharedSuggestionProvider.suggest(new String[] {"on", "off"}, b))
							.executes(context -> everyone(context.getSource(),
								StringArgumentType.getString(context, "what"), StringArgumentType.getString(context, "value"))))))
				.then(Commands.argument("player", StringArgumentType.word())
					.suggests((context, builder) -> SharedSuggestionProvider.suggest(
						TrustBook.get(context.getSource().getServer()).known().values(), builder))
					.then(Commands.argument("what", StringArgumentType.word()).suggests(TrustCommand::switches)
						.then(Commands.argument("value", StringArgumentType.word())
							.suggests((c, b) -> SharedSuggestionProvider.suggest(new String[] {"on", "off", "default"}, b))
							.executes(context -> player(context.getSource(), StringArgumentType.getString(context, "player"),
								StringArgumentType.getString(context, "what"), StringArgumentType.getString(context, "value"))))))));
	}

	private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> switches(
			com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
			com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
		return SharedSuggestionProvider.suggest(Arrays.stream(Trust.values()).map(Trust::id), builder);
	}

	private static int show(CommandSourceStack source) {
		if (source.getEntity() instanceof ServerPlayer op && PandoricalApi.hasCapability(op, Capabilities.SCREENS)) {
			TrustScreen.open(op);
			return 1;
		}
		MinecraftServer server = source.getServer();
		TrustBook book = TrustBook.get(server);
		StringBuilder line = new StringBuilder("Everyone:");
		for (Trust what : Trust.values()) line.append(' ').append(what.id()).append(' ').append(onOff(TrustRules.byDefault(server.overworld(), what)));
		source.sendSuccess(() -> Component.literal(line.toString()), false);
		book.known().forEach((id, name) -> {
			StringBuilder own = new StringBuilder();
			for (Trust what : Trust.values()) {
				Boolean choice = book.choice(id, what);
				if (choice != null) own.append(' ').append(what.id()).append(' ').append(onOff(choice));
			}
			if (!own.isEmpty()) source.sendSuccess(() -> Component.literal(name + ":" + own), false);
		});
		return 1;
	}

	private static int everyone(CommandSourceStack source, String whatId, String value) {
		Trust what = Trust.byId(whatId);
		if (what == null || !(value.equals("on") || value.equals("off"))) return fail(source);
		MinecraftServer server = source.getServer();
		boolean on = value.equals("on");
		if (what == Trust.PVP) server.overworld().getGameRules().set(GameRules.PVP, on, server);
		else TrustBook.get(server).chooseDefault(what, on);
		source.sendSuccess(() -> Component.literal("Everyone: " + what.id() + " " + value), true);
		return 1;
	}

	private static int player(CommandSourceStack source, String name, String whatId, String value) {
		Trust what = Trust.byId(whatId);
		if (what == null || !(value.equals("on") || value.equals("off") || value.equals("default"))) return fail(source);
		MinecraftServer server = source.getServer();
		TrustBook book = TrustBook.get(server);
		ServerPlayer online = server.getPlayerList().getPlayerByName(name);
		if (online != null) book.remember(online.getUUID(), online.getGameProfile().name());
		UUID who = online != null ? online.getUUID() : book.byName(name);
		if (who == null) {
			source.sendFailure(Component.literal("No player called " + name + " has been on this server."));
			return 0;
		}
		book.choose(who, what, value.equals("default") ? null : value.equals("on"));
		source.sendSuccess(() -> Component.literal(book.known().get(who) + ": " + what.id() + " " + value), true);
		return 1;
	}

	private static String onOff(boolean on) {
		return on ? "on" : "off";
	}

	private static int fail(CommandSourceStack source) {
		source.sendFailure(Component.literal("Switches: pvp, fire, explosives, animals; values: on, off, default."));
		return 0;
	}
}
