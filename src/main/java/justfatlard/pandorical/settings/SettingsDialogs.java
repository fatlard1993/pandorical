package justfatlard.pandorical.settings;

import justfatlard.pandorical.form.SimpleForm;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The mod menu and settings as forms, for a player whose client can't draw Pandorical's screens:
 * a vanilla Java client draws them as dialogs and a Bedrock one, through Geyser, as forms. Every
 * answer goes through the same checks a typed {@code /pandorical settings} command does.
 */
final class SettingsDialogs {
	private static final String LIST = "list:";
	private static final String REMOVE = "remove:";
	private static final String BACK = "back";
	private static final String SETTINGS = "settings";

	private SettingsDialogs() {}

	/** The settings this player may change: their own and, for an op, the server's. */
	static void open(ServerPlayer player, SettingsRegistry settings) {
		List<SettingsRegistry.GroupImpl> groups = groups(player, settings);
		if (groups.isEmpty()) {
			player.sendSystemMessage(Component.literal("No mod on this server has settings for you."));
			return;
		}
		if (groups.size() == 1) {
			group(player, settings, groups.getFirst());
			return;
		}
		SimpleForm form = SimpleForm.titled("Settings").text("Pick a mod.");
		for (SettingsRegistry.GroupImpl group : groups) form.button(groupId(group), heading(group));
		form.onAnswer(answer -> {
			for (SettingsRegistry.GroupImpl group : groups(player, settings)) {
				if (groupId(group).equals(answer.button())) group(player, settings, group);
			}
		}).open(player);
	}

	static void group(ServerPlayer player, SettingsRegistry settings, SettingsRegistry.GroupImpl group) {
		SimpleForm form = SimpleForm.titled(heading(group));
		for (SettingsRegistry.SettingImpl<?> setting : group.shown(player)) {
			if (setting instanceof SettingsRegistry.ListImpl list) {
				form.button(LIST + list.key, list.label() + "...");
			} else {
				setting.addField(form, player);
			}
		}
		// Back goes to the list of mods, which with one mod is this page again.
		if (groups(player, settings).size() > 1) form.button(BACK, "Back");
		form.onAnswer(answer -> {
			if (!allowed(player, group)) return;
			if (answer.button().equals(BACK)) {
				open(player, settings);
				return;
			}
			if (answer.button().startsWith(LIST)) {
				String key = answer.button().substring(LIST.length());
				for (SettingsRegistry.SettingImpl<?> setting : group.shown(player)) {
					if (setting instanceof SettingsRegistry.ListImpl list && list.key.equals(key)) {
						list(player, settings, group, list);
					}
				}
				return;
			}
			List<String> changed = new ArrayList<>();
			List<String> refused = new ArrayList<>();
			for (SettingsRegistry.SettingImpl<?> setting : group.shown(player)) {
				String value = answer.values().get(setting.id());
				if (value == null) continue;
				switch (setting.answer(player, value)) {
					case CHANGED -> changed.add(setting.label() + ": " + setting.shown(player));
					case REFUSED -> refused.add(setting.label());
					case UNCHANGED -> {}
				}
			}
			String said = changed.isEmpty() ? "Nothing changed." : "Changed " + String.join(", ", changed) + ".";
			player.sendSystemMessage(Component.literal(refused.isEmpty() ? said : said + " Not saved: " + String.join(", ", refused) + "."));
		}).open(player);
	}

	private static void list(ServerPlayer player, SettingsRegistry settings, SettingsRegistry.GroupImpl group, SettingsRegistry.ListImpl list) {
		var entries = list.entries(player);
		SimpleForm form = SimpleForm.titled(list.label())
			.text(entries.isEmpty() ? "Nothing here yet." : "Remove an entry:");
		entries.forEach((entry, shown) -> form.button(REMOVE + entry, "Remove " + shown));
		form.button(BACK, "Back").onAnswer(answer -> {
			if (!allowed(player, group) || !list.visible(player)) return;
			if (answer.button().startsWith(REMOVE)) {
				list.set(player, answer.button().substring(REMOVE.length()));
				list(player, settings, group, list);
			} else {
				group(player, settings, group);
			}
		}).open(player);
	}

	/** Every mod on the server, each opening a page about it. */
	static void mods(ServerPlayer player, SettingsRegistry settings) {
		SimpleForm form = SimpleForm.titled("Mods on this server");
		for (ModCatalog.ModInfo mod : ModCatalog.all()) form.button(mod.id(), mod.name());
		form.onAnswer(answer -> {
			ModCatalog.ModInfo mod = ModCatalog.find(answer.button());
			if (mod != null) mod(player, settings, mod);
		}).open(player);
	}

	private static void mod(ServerPlayer player, SettingsRegistry settings, ModCatalog.ModInfo mod) {
		SimpleForm form = SimpleForm.titled(mod.name() + " " + mod.version()).text(mod.description());
		if (!mod.authors().isEmpty()) form.text("By " + mod.authors());
		form.text(firstSection(mod.readme()));
		SettingsRegistry.GroupImpl own = null;
		for (SettingsRegistry.GroupImpl group : groups(player, settings)) {
			if (group.modId.equals(mod.id())) own = group;
		}
		SettingsRegistry.GroupImpl group = own;
		if (group != null) form.button(SETTINGS, "Settings");
		form.button(BACK, "Back").onAnswer(answer -> {
			if (answer.button().equals(SETTINGS) && group != null && allowed(player, group)) {
				group(player, settings, group);
			} else if (answer.button().equals(BACK)) {
				mods(player, settings);
			}
		}).open(player);
	}

	/** A readme's opening, up to its second heading: the whole of one is a wall of text in a form. */
	private static String firstSection(List<ModCatalog.Line> readme) {
		StringBuilder text = new StringBuilder();
		int headings = 0;
		for (ModCatalog.Line line : readme) {
			if (line.kind() == ModCatalog.Kind.HEADING && ++headings > 1) break;
			switch (line.kind()) {
				case TEXT, QUOTE -> text.append(line.text()).append('\n');
				case BULLET -> text.append("  ".repeat(line.level())).append("- ").append(line.text()).append('\n');
				case NUMBERED -> text.append(line.text()).append('\n');
				default -> {}
			}
		}
		return text.toString().strip();
	}

	private static List<SettingsRegistry.GroupImpl> groups(ServerPlayer player, SettingsRegistry settings) {
		List<SettingsRegistry.GroupImpl> groups = new ArrayList<>();
		for (SettingsRegistry.GroupImpl group : settings.groups()) {
			if (group.kind == SettingsRegistry.Kind.CLIENT || !allowed(player, group)) continue;
			if (!group.shown(player).isEmpty()) groups.add(group);
		}
		return groups;
	}

	/** Asked again at every answer: a player can lose op between being shown a form and sending it. */
	private static boolean allowed(ServerPlayer player, SettingsRegistry.GroupImpl group) {
		return !group.server || SettingsRegistry.isOp(player);
	}

	private static String groupId(SettingsRegistry.GroupImpl group) {
		return group.kind.name().toLowerCase(Locale.ROOT) + ":" + group.modId;
	}

	private static String heading(SettingsRegistry.GroupImpl group) {
		return group.server ? group.modName + " (server)" : group.modName;
	}
}
