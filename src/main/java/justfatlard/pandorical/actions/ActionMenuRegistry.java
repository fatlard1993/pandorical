package justfatlard.pandorical.actions;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.api.ActionMenuApi;
import justfatlard.pandorical.keybind.KeybindPool;
import justfatlard.pandorical.protocol.ActionMenusS2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * What every mod says belongs on a button, assembled into menus to offer a joining player.
 *
 * <p>Two kinds come out of here. The server's own menu is built rather than declared: every
 * keybind any mod has claimed already has a name and a slot, so each becomes a button without
 * anybody asking, and declared commands join them. A mod with more to say gets its own menu.
 */
public final class ActionMenuRegistry implements ActionMenuApi {
	public static final ActionMenuRegistry INSTANCE = new ActionMenuRegistry();

	/** Stable, because the client remembers by it. Never change it. */
	private static final String SERVER_MENU_ID = "pandorical:server";
	private static final String SERVER_MENU_NAME = "Server";

	/** What a keybind button wears when its mod has not said. A compass points at nothing. */
	private static final String DEFAULT_ICON = "minecraft:compass";

	/** As many as the payload carries. */
	private static final int MAX_MENUS = 16;
	private static final int MAX_BUTTONS = 64;

	private final List<Button> declaredButtons = new CopyOnWriteArrayList<>();
	private final Map<String, String> promotedKeybinds = new LinkedHashMap<>();
	private final Map<String, ActionMenusS2C.Menu> declaredMenus = new LinkedHashMap<>();

	private ActionMenuRegistry() {}

	@Override
	public void suggestButton(Button button) {
		if (button == null || button.command() == null || button.command().isBlank()) {
			Pandorical.LOGGER.warn("Ignoring action button with no command");
			return;
		}
		declaredButtons.add(button);
	}

	@Override
	public void suggestMenu(String id, String name, List<Button> buttons) {
		if (id == null || id.isBlank() || buttons == null || buttons.isEmpty()) {
			Pandorical.LOGGER.warn("Ignoring action menu '{}' with no id or no buttons", id);
			return;
		}
		if (declaredMenus.containsKey(id)) {
			Pandorical.LOGGER.warn("Action menu id '{}' already suggested - ignoring", id);
			return;
		}
		if (buttons.size() > MAX_BUTTONS) {
			Pandorical.LOGGER.warn("Action menu '{}' has {} buttons; only the first {} are sent",
				id, buttons.size(), MAX_BUTTONS);
		}

		List<ActionMenusS2C.Button> wire = new ArrayList<>();
		for (Button button : buttons.subList(0, Math.min(buttons.size(), MAX_BUTTONS))) {
			wire.add(new ActionMenusS2C.Button(
				icon(button.icon()), text(button.label()), text(button.command()),
				text(button.keyMapping())));
		}
		// No key: two mods both claiming one would fight over it, and the player has a screen for
		// picking keys that this cannot see.
		declaredMenus.put(id, new ActionMenusS2C.Menu(id, text(name), "", wire));
		Pandorical.LOGGER.info("Action menu suggested: '{}' (\"{}\", {} buttons)",
			id, name, wire.size());
	}

	@Override
	public void promoteKeybind(String keybindId, String icon) {
		if (keybindId == null || icon == null || icon.isBlank()) {
			// Both siblings say so when they refuse, and this one refusing in silence is the
			// button that never appears with nothing in the log to say why.
			Pandorical.LOGGER.warn(
				"[pandorical] promoteKeybind({}, {}) needs both a keybind id and an icon - ignored",
				keybindId, icon);
			return;
		}
		promotedKeybinds.put(keybindId, icon);
	}

	private static String icon(String icon) {
		return icon == null || icon.isBlank() ? DEFAULT_ICON : icon;
	}

	private static String text(String value) {
		return value == null ? "" : value;
	}

	/**
	 * The server's own menu: a button for every claimed keybind, then every declared command.
	 *
	 * <p>Only keybinds a mod actually put forward, never every one it claimed. A menu that mirrored
	 * the controls screen would be a slower controls screen; what earns a place here is the thing
	 * you would otherwise have to remember a command for.
	 *
	 * <p>Built at send time rather than at registration, because keybind slots are claimed in mod
	 * load order and a menu assembled halfway through that would be missing whoever loaded last.
	 */
	private ActionMenusS2C.Menu serverMenu() {
		List<ActionMenusS2C.Button> buttons = new ArrayList<>();

		for (KeybindPool.Claim claim : KeybindPool.INSTANCE.claims()) {
			String icon = promotedKeybinds.get(claim.id());
			if (icon == null) continue;
			buttons.add(new ActionMenusS2C.Button(
				icon, claim.displayName(), "", "key.pandorical.action" + (claim.slot() + 1)));
		}
		for (Button declared : declaredButtons) {
			buttons.add(new ActionMenusS2C.Button(
				icon(declared.icon()), text(declared.label()), text(declared.command()), ""));
		}

		if (buttons.size() > MAX_BUTTONS) buttons = buttons.subList(0, MAX_BUTTONS);
		return new ActionMenusS2C.Menu(SERVER_MENU_ID, SERVER_MENU_NAME, "", buttons);
	}

	/**
	 * The game's own, which every client has whether the server runs one mod or none.
	 *
	 * <p>Only the corners: things worth reaching for and not worth a key of their own. Inventory,
	 * chat, the command line, swapping hands and dropping are all things a player does constantly
	 * and already has a finger on, and putting them here would be a slower way to do a fast thing.
	 *
	 * <p>The player list is left out for a different reason: the game shows it only while its key
	 * is held, and a button holds a key for two ticks, so it would blink and be gone. The social
	 * screen is the one that actually lists who is on.
	 */
	private static ActionMenusS2C.Menu gameMenu() {
		List<ActionMenusS2C.Button> buttons = new ArrayList<>();
		buttons.add(key("minecraft:player_head", "Players", "key.socialInteractions"));
		buttons.add(key("minecraft:knowledge_book", "Advancements", "key.advancements"));
		buttons.add(key("minecraft:spyglass", "Perspective", "key.togglePerspective"));
		buttons.add(key("minecraft:painting", "Screenshot", "key.screenshot"));
		return new ActionMenusS2C.Menu("pandorical:game", "Game", "", buttons);
	}

	private static ActionMenusS2C.Button key(String icon, String label, String mapping) {
		return new ActionMenusS2C.Button(icon, label, "", mapping);
	}

	/** @hidden Called once the player is ready, beside the keybind declarations. */
	public void offerTo(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, ActionMenusS2C.TYPE)) return;

		List<ActionMenusS2C.Menu> menus = new ArrayList<>();
		menus.add(gameMenu());
		ActionMenusS2C.Menu server = serverMenu();
		if (!server.buttons().isEmpty()) menus.add(server);
		menus.addAll(declaredMenus.values());

		if (menus.isEmpty()) return;
		if (menus.size() > MAX_MENUS) menus = menus.subList(0, MAX_MENUS);
		ServerPlayNetworking.send(player, new ActionMenusS2C(menus));
	}
}
