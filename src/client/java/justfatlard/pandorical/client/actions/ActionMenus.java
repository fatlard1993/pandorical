package justfatlard.pandorical.client.actions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.platform.InputConstants;
import justfatlard.pandorical.client.mixin.KeyMappingAccessor;
import justfatlard.pandorical.client.settings.ClientSettings;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Action menus: a player's own grids of buttons, each opened by a key of their choosing, each
 * button pressing a key, running a command, or opening another menu - so a menu can be a page of a
 * bigger one, with no key of its own.
 *
 * <p>Kept on this machine, one set per account, in {@code config/pandorical-action-menus.json}:
 * players who share a machine keep their own. Edited from Pandorical's page of the mods menu.
 */
public final class ActionMenus {
	private ActionMenus() {}

	/** One menu: its name, the key that opens it (an input name, empty for none) and its buttons. */
	public static final class Menu {
		/**
		 * Built from what the server offers rather than by the player.
		 *
		 * <p>Its buttons are not theirs to change: it is rebuilt from scratch every time they join,
		 * so a mod that adds a button has added it for everybody, and one that goes away takes its
		 * buttons with it. Only the key is the player's, and that is kept for them.
		 *
		 * <p>Never saved with the menus: a built-in menu that outlived the server offering it would
		 * be a grid of commands that no longer exist.
		 */
		public transient boolean builtin;

		/** What a button opening this menu names it by, so it survives the menu being renamed. */
		public String id = java.util.UUID.randomUUID().toString();
		public String name = "Actions";
		public String key = "";
		public List<Entry> buttons = new ArrayList<>();
	}

	/** One button: its icon (an item id), a label, and what it does. */
	public static final class Entry {
		public String icon = "minecraft:compass";
		public String label = "";
		/** {@link #COMMAND}, {@link #KEY} or {@link #MENU}. */
		public String type = COMMAND;
		public String command = "";
		/** A key mapping's name, e.g. {@code key.inventory}. */
		public String keyMapping = "";
		/** The {@link Menu#id} of the menu it opens. */
		public String menu = "";

		/** For tests: the same button, as a separate one. */
		public Entry copyForTest() {
			return copy();
		}

		Entry copy() {
			Entry out = new Entry();
			out.icon = icon;
			out.label = label;
			out.type = type;
			out.command = command;
			out.keyMapping = keyMapping;
			out.menu = menu;
			return out;
		}
	}

	public static final String COMMAND = "command";
	public static final String KEY = "key";
	public static final String MENU = "menu";

	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("pandorical-action-menus.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Every account's menus on this machine, by account id. */
	private static Map<String, List<Menu>> byAccount = load();

	/** Key mappings pressed by a button, and how many ticks until they are let go. */
	private static final Map<KeyMapping, Integer> pressing = new HashMap<>();

	/** How long a button holds a key down: long enough for a movement key to be seen. */
	private static final int PRESS_TICKS = 2;

	private static boolean editorWanted;

	/** A command the mods screen asked to put on a menu, waiting for a screen to ask which. */
	private static String addWanted;

	/**
	 * The editor, asked for from the mods screen.
	 *
	 * <p>Asked for from a server-drawn screen, so it arrives on the network thread and is acted on
	 * in {@link #tick}: opening a screen from under the one the server is still drawing puts the
	 * new one behind it.
	 */
	private static void asked(String value) {
		editorWanted = true;
	}

	/**
	 * Put a command the player asked to keep on one of their menus, once there is a tick to do it
	 * in. Same reason as {@link #asked}: the request arrives while the server is still drawing the
	 * screen it came from.
	 */
	public static void addToMenu(String command) {
		addWanted = command;
	}

	/**
	 * A button for this command, wearing whatever the mod that promoted it chose.
	 *
	 * <p>A command listed in the mods screen and also promoted is the same command: it arrives
	 * already named and pictured rather than as its own text on a sheet of paper.
	 */
	public static Entry buttonFor(String command) {
		String wanted = command.startsWith("/") ? command.substring(1) : command;
		for (Promoted known : promoted) {
			String theirs = known.button().command;
			if (theirs.startsWith("/")) theirs = theirs.substring(1);
			if (theirs.equals(wanted)) return known.button().copy();
		}
		Entry entry = new Entry();
		entry.icon = "minecraft:paper";
		entry.label = wanted;
		entry.command = wanted;
		return entry;
	}

	/**
	 * The one key that opens the menu of menus, as a real key mapping rather than a raw key.
	 *
	 * <p>A menu's own key is matched against the keyboard event, which is fine for a keyboard and
	 * useless to anything else: a controller presses key mappings, so a pad could reach every
	 * keybind in the game and none of these. This is the way in that a pad can be bound to, and
	 * that a player can move in the ordinary controls screen.
	 */
	private static KeyMapping menusKey;

	public static KeyMapping menusKey() {
		return menusKey;
	}

	public static void register() {
		menusKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.pandorical.menus",
			InputConstants.KEY_J, justfatlard.pandorical.client.keybind.KeybindManager.category()));

		ClientSettings.INSTANCE.group("pandorical", "Pandorical")
			.choice("actionMenus", "Action menus",
				"Grids of buttons you open with a key: each presses a key or runs a command",
				Map.of("edit", "Edit..."), () -> "edit", ActionMenus::asked);
		ClientTickEvents.END_CLIENT_TICK.register(ActionMenus::tick);
	}

	/** The menus of whoever is signed in. Changes to the list are kept by {@link #save}. */
	public static List<Menu> mine() {
		return byAccount.computeIfAbsent(account(), a -> new ArrayList<>());
	}

	private static String account() {
		return Minecraft.getInstance().getUser().getProfileId().toString();
	}

	public static void save() {
		try {
			justfatlard.pandorical.ConfigFiles.write(FILE, writer -> GSON.toJson(byAccount, writer));
		} catch (IOException e) {
			justfatlard.pandorical.Pandorical.LOGGER.warn("[pandorical] could not save action menus", e);
		}
	}

	/**
	 * Every account's menus, off the disk.
	 *
	 * <p>A file that will not parse is set aside rather than read as an empty one. Both look the
	 * same from here, and the difference is everything: treating damage as emptiness means the
	 * next edit saves over it, and the menus are not gone until that happens.
	 */
	private static Map<String, List<Menu>> load() {
		if (!Files.exists(FILE)) return new HashMap<>();
		try (Reader reader = Files.newBufferedReader(FILE)) {
			Map<String, List<Menu>> read = GSON.fromJson(reader, new TypeToken<Map<String, List<Menu>>>() {}.getType());
			return read != null ? new HashMap<>(read) : new HashMap<>();
		} catch (IOException | RuntimeException e) {
			java.nio.file.Path kept = justfatlard.pandorical.ConfigFiles.setAside(FILE);
			justfatlard.pandorical.Pandorical.LOGGER.warn(
				"[pandorical] could not read action menus; kept as {}",
				kept == null ? "(could not be kept)" : kept.getFileName(), e);
			damaged = kept;
			return new HashMap<>();
		}
	}

	/** Where a save that would not parse was put, so the player can be told once they are in. */
	private static java.nio.file.Path damaged;

	/**
	 * Say so, once, if the menus could not be read. Told on joining rather than at load: there is
	 * no player to tell when a client starts, and a screen full of nothing with no explanation is
	 * how somebody concludes the mod ate their work.
	 */
	public static void sayIfDamaged() {
		if (damaged == null) return;
		Minecraft mc = Minecraft.getInstance();
		// Joining can beat the player into existence. Keep it for the next join rather than
		// clearing it here, or the one thing worth saying is the thing that goes missing.
		if (mc.player == null) return;
		java.nio.file.Path kept = damaged;
		damaged = null;
		mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
			"Pandorical could not read your action menus, so it has started a fresh set. The old"
				+ " file is kept as " + kept.getFileName() + " if you want to look at it."));
	}

	/**
	 * One button a mod put its name to, and which menu it arrived in.
	 *
	 * <p>A mod that promotes a button has already done the work a player would otherwise do by
	 * hand: decided the thing is worth reaching for, named it, and chosen an icon for it. Keeping
	 * the list means adding one later is a pick rather than a form.
	 */
	public record Promoted(String group, Entry button) {}

	private static final List<Promoted> promoted = new ArrayList<>();

	/** Everything the server put its name to this session, in the order it offered them. */
	public static List<Promoted> promoted() {
		return List.copyOf(promoted);
	}

	/**
	 * What this server offers, rebuilt from scratch.
	 *
	 * <p>Called on every join and replacing whatever was there, because that is the whole point:
	 * the menus the server offers are a view of what the mods here actually promote, not a copy
	 * taken once and left to rot. A twelfth emote becoming a thirteenth turns up by itself.
	 *
	 * <p>The player's own menus are not touched. Their choice of key for a built-in is, though,
	 * which is why it is kept separately and put back here.
	 */
	public static void offered(java.util.List<justfatlard.pandorical.protocol.ActionMenusS2C.Menu> menus) {
		promoted.clear();
		builtins.clear();

		for (justfatlard.pandorical.protocol.ActionMenusS2C.Menu offered : menus) {
			Menu menu = new Menu();
			menu.builtin = true;
			menu.id = offered.id();
			menu.name = offered.name();
			menu.key = builtinKeys.containsKey(offered.id())
				? builtinKeys.get(offered.id())
				: suggested(offered.key());
			for (var button : offered.buttons()) {
				Entry entry = entryOf(button);
				menu.buttons.add(entry);
				promoted.add(new Promoted(offered.name(), entry));
			}
			builtins.add(menu);
		}
		addMetaMenu();
		tellOnce();
	}

	/** The preference that remembers the player has been shown where the menus are. */
	private static final String TOLD = "action_menus_notice_shown";

	/**
	 * Say once, ever, that these exist and which key opens them.
	 *
	 * <p>A server's menus arrive silently and draw nothing until a key is pressed, so a player who
	 * never reads the readme has no way to find out they are there. Said in the player's own key,
	 * because it is rebindable and the readme's "J" stops being true the moment they move it.
	 */
	private static void tellOnce() {
		if (builtins.isEmpty()) return;
		if (justfatlard.pandorical.client.settings.ClientPrefs.getBoolean(TOLD, false)) return;
		Minecraft mc = Minecraft.getInstance();
		// Joining can beat the player into existence; better told on the next server than marked
		// as told to nobody.
		if (mc.player == null || menusKey == null) return;
		justfatlard.pandorical.client.settings.ClientPrefs.set(TOLD, true);
		mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
			"This server offers action menus: press ")
			.append(menusKey.getTranslatedKeyMessage())
			.append(" to open them. You can add your own from Pandorical's page in /pandorical mods."));
	}

	/**
	 * A key the server suggested for one of its menus, taken only where the game is not already
	 * using it. The player's own choice outranks this wherever they have made one; this is only
	 * the first offer. A server naming the keys the player walks with would otherwise take
	 * walking away from them, with nothing on screen to say who did it.
	 */
	private static String suggested(String key) {
		if (key == null || key.isEmpty()) return "";
		for (net.minecraft.client.KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
			if (key.equals(mapping.saveString())) {
				justfatlard.pandorical.Pandorical.LOGGER.info(
					"[pandorical] a server suggested {} for a menu, which is already bound to {} - left unset",
					key, mapping.getName());
				return "";
			}
		}
		return key;
	}

	/** Where the one key lands, until the player moves it. Vanilla leaves J alone. */
	private static final String META_KEY = "key.keyboard.j";
	private static final String META_ID = "pandorical:menus";

	/**
	 * One key for all of them.
	 *
	 * <p>Every menu the server offers wants a key, and there are only so many keys anybody will
	 * learn. So there is a menu of menus on a single key, each button opening one of the others,
	 * and a player who wants a favourite on its own key can still give it one.
	 *
	 * <p>Built here rather than sent, because it is a view of whatever else arrived: a mod added
	 * after this one was written is on it without having asked.
	 */
	private static void addMetaMenu() {
		if (builtins.size() < 2) return;

		Menu meta = new Menu();
		meta.builtin = true;
		meta.id = META_ID;
		meta.name = "Menus";
		// The controls screen owns this one. Reading it back off the KeyMapping is what makes a
		// rebind there move the key rather than add a second: the hub used to answer both to
		// whatever was bound and to J for ever, and no screen said why.
		meta.key = menusKey != null ? menusKey.saveString() : META_KEY;
		for (Menu target : builtins) {
			Entry open = new Entry();
			// The first thing on a menu stands for it: Emotes wears a heart, Arena a sword.
			open.icon = target.buttons.isEmpty() ? "minecraft:book" : target.buttons.getFirst().icon;
			open.label = target.name;
			open.type = MENU;
			open.menu = target.id;
			meta.buttons.add(open);
		}
		builtins.addFirst(meta);
	}

	/** The server's menus, rebuilt each join and belonging to nobody. */
	private static final List<Menu> builtins = new ArrayList<>();

	// Declared before the field that reads it: static fields are initialised in the order they
	// are written, so a path declared after its reader is still null when the reader runs, and
	// Files.exists(null) throws inside a class initialiser - which fails the whole client.
	private static final Path KEYS = FabricLoader.getInstance()
		.getConfigDir().resolve("pandorical").resolve("action-menu-keys.json");

	/** The key a player chose for a built-in, which is the one thing about it that is theirs. */
	private static Map<String, String> builtinKeys = loadBuiltinKeys();

	private static Map<String, String> loadBuiltinKeys() {
		if (!Files.exists(KEYS)) return new HashMap<>();
		try (Reader reader = Files.newBufferedReader(KEYS)) {
			Map<String, String> read = GSON.fromJson(reader, new TypeToken<Map<String, String>>() {}.getType());
			return read != null ? new HashMap<>(read) : new HashMap<>();
		} catch (IOException | RuntimeException e) {
			justfatlard.pandorical.Pandorical.LOGGER.warn("[pandorical] could not read action menu keys", e);
			return new HashMap<>();
		}
	}

	/** Remember what this player opens a built-in menu with. */
	public static void rememberBuiltinKey(Menu menu) {
		if (!menu.builtin) return;
		builtinKeys.put(menu.id, menu.key);
		try {
			justfatlard.pandorical.ConfigFiles.write(KEYS, writer -> GSON.toJson(builtinKeys, writer));
		} catch (IOException e) {
			justfatlard.pandorical.Pandorical.LOGGER.warn("[pandorical] could not save action menu keys", e);
		}
	}

	/**
	 * The menu already opening with this key, if any, ignoring the one being edited.
	 *
	 * <p>{@link #keyPressed} takes the first match, so a second menu on the same key never opens
	 * and nothing on screen says why.
	 */
	public static Menu usingKey(String key, Menu except) {
		if (key == null || key.isEmpty()) return null;
		for (Menu menu : all()) {
			if (menu != except && key.equals(menu.key)) return menu;
		}
		return null;
	}

	/** The server's menus and then the player's: everything openable, in the order shown. */
	public static List<Menu> all() {
		List<Menu> out = new ArrayList<>(builtins);
		out.addAll(mine());
		return out;
	}

	private static Entry entryOf(justfatlard.pandorical.protocol.ActionMenusS2C.Button button) {
		Entry entry = new Entry();
		entry.icon = button.icon();
		entry.label = button.label();
		entry.type = button.keyMapping().isEmpty() ? COMMAND : KEY;
		entry.command = button.command();
		entry.keyMapping = button.keyMapping();
		return entry;
	}

	/** Nothing the last server offered survives leaving it: its commands may not exist on the next. */
	public static void forgetPromoted() {
		promoted.clear();
		builtins.clear();
	}

	private static void tick(Minecraft mc) {
		// The hub key, drained even with a screen up so a held press cannot pile up.
		boolean wanted = menusKey != null && menusKey.consumeClick();
		if (wanted && mc.player != null && mc.gui.screen() == null) {
			Menu hub = menuById(META_ID);
			if (hub == null && !builtins.isEmpty()) hub = builtins.getFirst();
			if (hub != null && !hub.buttons.isEmpty()) {
				mc.gui.setScreen(new ActionMenuScreen(hub, null));
				return;
			}
		}

		pressing.entrySet().removeIf(pressed -> {
			if (pressed.getValue() > 1) {
				pressed.setValue(pressed.getValue() - 1);
				return false;
			}
			pressed.getKey().setDown(false);
			return true;
		});

		// Asked for from the mods menu, whose own screen the server may still be redrawing this
		// tick: opened after, so it is not drawn over.
		// Handed the screen they were on, so Done puts them back on it. Passing null closed
		// everything and left them standing in the world, and a second command meant finding the
		// mod, its Commands tab and their place in the list all over again.
		if (editorWanted) {
			editorWanted = false;
			mc.gui.setScreen(new MenuListScreen(mc.gui.screen()));
			return;
		}

		if (addWanted != null) {
			String command = addWanted;
			addWanted = null;
			mc.gui.setScreen(new AddToMenuScreen(mc.gui.screen(), buttonFor(command)));
			return;
		}

	}

	/**
	 * A key pressed with no screen up: the first menu it opens, opened. Called for every key event,
	 * and only a press counts, so holding it does not open the menu again once it is put away.
	 */
	public static void keyPressed(int action, KeyEvent event) {
		Minecraft mc = Minecraft.getInstance();
		if (action != InputConstants.PRESS || mc.player == null || mc.gui.screen() != null) return;
		String pressed = InputConstants.getKey(event).getName();
		for (Menu menu : all()) {
			// The hub is opened from its KeyMapping in tick, so matching it here as well would
			// open it twice and close it again on the same press.
			if (META_ID.equals(menu.id)) continue;
			if (menu.key.equals(pressed) && !menu.buttons.isEmpty()) {
				mc.gui.setScreen(new ActionMenuScreen(menu, null));
				return;
			}
		}
	}

	/** Do what a button says, once whatever screen it was on has closed. */
	public static void run(Entry entry) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		if (COMMAND.equals(entry.type)) {
			String command = entry.command.strip();
			if (command.startsWith("/")) command = command.substring(1);
			if (!command.isEmpty()) mc.player.connection.sendCommand(command);
			return;
		}
		KeyMapping mapping = mapping(entry.keyMapping);
		if (mapping == null) return;
		// Down for a moment and clicked once, which between them is a key press to anything that
		// asks: what counts clicks, and what looks at whether it is held.
		mapping.setDown(true);
		KeyMappingAccessor clicks = (KeyMappingAccessor) mapping;
		clicks.pandorical$setClickCount(clicks.pandorical$getClickCount() + 1);
		pressing.put(mapping, PRESS_TICKS);
	}

	/** The player's menu with this id, or null: it may have been removed since the button was made. */
	public static Menu menuById(String id) {
		for (Menu menu : all()) {
			if (menu.id.equals(id)) return menu;
		}
		return null;
	}

	public static KeyMapping mapping(String name) {
		for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
			if (mapping.getName().equals(name)) return mapping;
		}
		return null;
	}
}
