package justfatlard.pandorical.actions;

import justfatlard.pandorical.api.ActionMenuApi;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.form.Forms;
import justfatlard.pandorical.form.SimpleForm;
import justfatlard.pandorical.keybind.KeybindPool;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The action menus as a form, for a player whose client has no menus of Pandorical's: the brief,
 * the mods, settings and notices, every mod key, and every button a mod suggested that runs a
 * command (a button that presses a client key has nothing to press here).
 *
 * <p>Reached through the game's own Quick Actions (a dialog shipped as data and tagged
 * {@code #minecraft:quick_actions}; on Bedrock, {@code /geyser quickactions}, which on Fabric
 * without a permissions mod is for ops) or
 * {@code /pandorical actions}.</p>
 */
public final class QuickActions {
	/** The click the shipped {@code pandorical:quick_actions} dialog sends. */
	private static final Identifier OPEN = Identifier.fromNamespaceAndPath("pandorical", "quick_actions");
	private static final String KEY = "key:";
	private static final String COMMAND = "command:";
	private static final String PLAYER = "{player}";
	private static final String PLAYERS = "{players}";
	private static final String NOTICES_KEY = "pandorical:notices";

	private QuickActions() {}

	public static void register() {
		Forms.onClick(OPEN, QuickActions::open);
	}

	public static void open(ServerPlayer player) {
		int waiting = PandoricalApi.notices().waiting(player);
		SimpleForm form = SimpleForm.titled("Quick actions")
			.button("brief", "Welcome")
			.button("changes", "What's new")
			.button("mods", "Mods")
			.button("settings", "Settings")
			.button("notices", waiting == 0 ? "Notices" : "Notices (" + waiting + ")");
		for (KeybindPool.Claim claim : KeybindPool.INSTANCE.claims()) {
			// Pandorical's own notices key is the Notices button above, with its count.
			if (claim.id().equals(NOTICES_KEY)) continue;
			form.button(KEY + claim.id(), claim.displayName());
		}
		List<ActionMenuApi.Button> commands = ActionMenuRegistry.INSTANCE.commandButtons();
		for (int i = 0; i < commands.size(); i++) form.button(COMMAND + i, commands.get(i).label());

		form.onAnswer(answer -> {
			String pressed = answer.button();
			switch (pressed) {
				case "brief", "changes", "mods", "settings", "notices" -> run(player, "pandorical " + pressed);
				default -> {
					if (pressed.startsWith(KEY)) {
						KeybindPool.INSTANCE.pressFromMenu(player, pressed.substring(KEY.length()));
					} else if (pressed.startsWith(COMMAND)) {
						int index = Integer.parseInt(pressed.substring(COMMAND.length()));
						// Built afresh: a mod suggesting a button since this form opened shifts the list.
						List<ActionMenuApi.Button> now = ActionMenuRegistry.INSTANCE.commandButtons();
						if (index < now.size() && now.get(index).label().equals(commands.get(index).label())) {
							command(player, now.get(index));
						}
					}
				}
			}
		}).open(player);
	}

	/** A button whose command names somebody asks who first; {@code {players}} asks one at a time. */
	private static void command(ServerPlayer player, ActionMenuApi.Button button) {
		String command = button.command();
		String placeholder = command.contains(PLAYERS) ? PLAYERS : command.contains(PLAYER) ? PLAYER : null;
		if (placeholder == null) {
			run(player, command);
			return;
		}
		Map<String, String> others = new LinkedHashMap<>();
		for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
			if (other != player) others.put(other.getGameProfile().name(), other.getGameProfile().name());
		}
		if (others.isEmpty()) {
			player.sendSystemMessage(Component.literal("Nobody else is online."));
			return;
		}
		SimpleForm.titled(button.label())
			.choice("who", "Who", others, others.keySet().iterator().next())
			.submit("Go")
			.onAnswer(answer -> {
				String who = answer.values().get("who");
				// Still online, since anyone named in the form may have left before it came back.
				if (who != null && player.level().getServer().getPlayerList().getPlayerByName(who) != null) {
					run(player, command.replace(placeholder, who));
				}
			}).open(player);
	}

	/** As the player, exactly as if they had typed it. */
	private static void run(ServerPlayer player, String command) {
		player.level().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
	}
}
