package justfatlard.pandorical.client.actions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.platform.InputConstants;
import justfatlard.pandorical.client.mixin.KeyMappingAccessor;
import justfatlard.pandorical.client.settings.ClientSettings;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
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

	public static void register() {
		ClientSettings.INSTANCE.group("pandorical", "Pandorical")
			.choice("actionMenus", "Action menus",
				"Grids of buttons you open with a key: each presses a key or runs a command",
				Map.of("edit", "Edit..."), () -> "edit", v -> editorWanted = true);
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
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(byAccount, writer);
			}
		} catch (IOException e) {
			justfatlard.pandorical.Pandorical.LOGGER.warn("[pandorical] could not save action menus", e);
		}
	}

	private static Map<String, List<Menu>> load() {
		if (!Files.exists(FILE)) return new HashMap<>();
		try (Reader reader = Files.newBufferedReader(FILE)) {
			Map<String, List<Menu>> read = GSON.fromJson(reader, new TypeToken<Map<String, List<Menu>>>() {}.getType());
			return read != null ? new HashMap<>(read) : new HashMap<>();
		} catch (IOException | RuntimeException e) {
			justfatlard.pandorical.Pandorical.LOGGER.warn("[pandorical] could not read action menus", e);
			return new HashMap<>();
		}
	}

	private static void tick(Minecraft mc) {
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
		if (editorWanted) {
			editorWanted = false;
			mc.gui.setScreen(new MenuListScreen(null));
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
		for (Menu menu : mine()) {
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
		for (Menu menu : mine()) {
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
