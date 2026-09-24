package justfatlard.pandorical.client.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.api.KeybindApi;
import justfatlard.pandorical.keybind.KeybindPool;
import justfatlard.pandorical.protocol.KeyPressC2S;
import justfatlard.pandorical.protocol.KeyReleaseC2S;
import justfatlard.pandorical.protocol.KeybindBindingsC2S;
import justfatlard.pandorical.protocol.KeybindDeclarationsS2C;
import justfatlard.pandorical.protocol.KeybindDefaultsS2C;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A fixed pool of rebindable KeyMappings, registered at client startup (the only time the options
 * system accepts them) and given meaning per server. Only slots the server claimed are forwarded.
 */
@Environment(EnvType.CLIENT)
public final class KeybindManager {
	private KeybindManager() {}

	private static final int MAX_SLOTS = KeybindPool.MAX_SLOTS;
	private static final boolean[] wasDown = new boolean[MAX_SLOTS];

	private static final KeyMapping[] pool = new KeyMapping[MAX_SLOTS];
	private static final Set<Integer> claimedSlots = ConcurrentHashMap.newKeySet();
	/** The slot the next key press binds, or -1. */
	private static volatile int rebinding = -1;

	/** Call once from client mod init, never later. */
	public static void init() {
		// Servers name default keys by these numbers.
		if (KeybindApi.letter('G') != InputConstants.KEY_G || KeybindApi.letter('B') != InputConstants.KEY_B) {
			Pandorical.LOGGER.error("InputConstants no longer numbers keys the way KeybindApi.letter does;"
				+ " every keybind default will land on the wrong key");
		}
		KeyMapping.Category category = category();
		for (int i = 0; i < MAX_SLOTS; i++) {
			pool[i] = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.pandorical.action" + (i + 1), KeybindPool.poolDefaultKey(i), category));
		}
	}

	public static void handleDeclarations(KeybindDeclarationsS2C payload) {
		claimedSlots.clear();
		for (Integer slot : payload.claimedSlots()) {
			if (slot != null && slot >= 0 && slot < MAX_SLOTS) claimedSlots.add(slot);
		}
		Pandorical.LOGGER.debug("Server declared keybind slots: {}", claimedSlots);
		// Only the client can read its bindings.
		sendBindings();
	}

	private static final int MOST_REMEMBERED = 512;
	/** One {@code id@slot} per line for each keybind whose default was already offered. */
	private static final Path DEFAULTS_APPLIED = FabricLoader.getInstance()
		.getConfigDir().resolve("pandorical").resolve("keybind-defaults.txt");

	/**
	 * Binds each default key to an unbound slot, once per {@code id@slot} ever seen, bound or not,
	 * so a key the player later clears or moves is never put back.
	 */
	public static void applyDefaults(KeybindDefaultsS2C payload) {
		Set<String> applied = new LinkedHashSet<>();
		try {
			if (Files.exists(DEFAULTS_APPLIED)) {
				for (String line : Files.readAllLines(DEFAULTS_APPLIED)) {
					if (!line.isBlank()) applied.add(line.trim());
				}
			}
		} catch (IOException e) {
			Pandorical.LOGGER.warn("Could not read {}: {}", DEFAULTS_APPLIED, e.toString());
			return;
		}

		boolean bound = false;
		boolean remembered = false;
		for (var entry : payload.entries()) {
			if (entry.slot() < 0 || entry.slot() >= MAX_SLOTS || pool[entry.slot()] == null) continue;
			if (!applied.add(entry.id() + "@" + entry.slot())) continue;
			remembered = true;
			if (!pool[entry.slot()].isUnbound()) continue;
			pool[entry.slot()].setKey(InputConstants.Type.KEYBOARD.getOrCreate(entry.key()));
			bound = true;
			Pandorical.LOGGER.info("Bound {} to its default key", entry.id());
		}

		if (remembered) {
			Iterator<String> oldest = applied.iterator();
			while (applied.size() > MOST_REMEMBERED && oldest.hasNext()) {
				oldest.next();
				oldest.remove();
			}
			try {
				Files.createDirectories(DEFAULTS_APPLIED.getParent());
				Files.write(DEFAULTS_APPLIED, applied);
			} catch (IOException e) {
				Pandorical.LOGGER.warn("Could not write {}: {}", DEFAULTS_APPLIED, e.toString());
			}
		}
		if (bound) {
			KeyMapping.resetMapping();
			Minecraft client = Minecraft.getInstance();
			if (client != null && client.options != null) client.options.save();
		}
		// Always, not only when something changed. The server asks this client what its keys are
		// so it can name them back to the player, and a client that already had the right key
		// changes nothing here - which used to mean it never answered, and the server went on
		// believing the slot was empty.
		sendBindings();
	}

	public static void sendBindings() {
		if (!ClientPlayNetworking.canSend(KeybindBindingsC2S.TYPE)) return;
		List<String> keys = new ArrayList<>(MAX_SLOTS);
		for (int i = 0; i < MAX_SLOTS; i++) {
			keys.add(pool[i] == null || pool[i].isUnbound() ? "" : pool[i].getTranslatedKeyMessage().getString());
		}
		ClientPlayNetworking.send(new KeybindBindingsC2S(keys));
	}

	/** A negative slot cancels. */
	public static void handleRebindRequest(int slot) {
		rebinding = slot >= 0 && slot < MAX_SLOTS ? slot : -1;
	}

	/**
	 * Takes the press as the pending rebind; true when it was spent and must go no further. Escape
	 * unbinds, as in the controls screen; keys already bound elsewhere are allowed.
	 */
	public static boolean captureKey(KeyEvent event) {
		int slot = rebinding;
		if (slot < 0) return false;
		rebinding = -1;
		Minecraft client = Minecraft.getInstance();
		if (pool[slot] != null) {
			pool[slot].setKey(event.key() == InputConstants.KEY_ESCAPE ? InputConstants.UNKNOWN : InputConstants.getKey(event));
			KeyMapping.resetMapping();
			if (client != null && client.options != null) client.options.save();
		}
		sendBindings();
		return true;
	}

	/**
	 * Null out of range. For non-keyboard input: {@link #tick} reads {@code consumeClick}, so a
	 * click made on the mapping reaches the server like a key press.
	 */
	private static KeyMapping.Category category;

	/**
	 * The one "Pandorical" heading in the controls screen.
	 *
	 * <p>Registered once and handed out, because registering a category twice throws and more than
	 * one thing here wants keys under it - the pool, and the key that opens the action menus.
	 */
	public static synchronized KeyMapping.Category category() {
		if (category == null) {
			category = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath(Pandorical.MOD_ID, "pandorical"));
		}
		return category;
	}

	public static KeyMapping poolMapping(int slot) {
		return slot >= 0 && slot < MAX_SLOTS ? pool[slot] : null;
	}

	/** Drains unclaimed clicks too, so they cannot pile up. */
	public static void tick(Minecraft client) {
		if (pool[0] == null) return;
		for (int i = 0; i < MAX_SLOTS; i++) {
			while (pool[i].consumeClick()) {
				if (claimedSlots.contains(i) && ClientPlayNetworking.canSend(KeyPressC2S.TYPE)) {
					ClientPlayNetworking.send(new KeyPressC2S(i));
				}
			}
			boolean down = pool[i].isDown();
			if (wasDown[i] && !down && claimedSlots.contains(i)
					&& ClientPlayNetworking.canSend(KeyReleaseC2S.TYPE)) {
				ClientPlayNetworking.send(new KeyReleaseC2S(i));
			}
			wasDown[i] = down;
		}
	}

	public static void clear() {
		claimedSlots.clear();
		rebinding = -1;
	}
}
