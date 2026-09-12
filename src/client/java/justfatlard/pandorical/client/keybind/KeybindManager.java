package justfatlard.pandorical.client.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.api.KeybindApi;
import justfatlard.pandorical.keybind.KeybindPool;
import justfatlard.pandorical.protocol.KeyPressC2S;
import justfatlard.pandorical.protocol.KeyReleaseC2S;
import justfatlard.pandorical.protocol.KeybindBindingsC2S;
import justfatlard.pandorical.protocol.KeybindDeclarationsS2C;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import justfatlard.pandorical.protocol.KeybindDefaultsS2C;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.input.KeyEvent;

/**
 * The client half of the pooled keybind capability: a fixed pool of real,
 * rebindable KeyMappings registered at normal client startup (the only time
 * the options system accepts them), whose meaning is assigned per server.
 *
 * <p>Slot 1 defaults to G and slot 2 to B, the rest start unbound; all live under the
 * "Pandorical" controls category with shipped default names ("Pandorical
 * Action N") that a server's synced lang overrides for its claimed slots.
 * Presses are only forwarded for slots the current server declared, so
 * unclaimed keys are inert.
 */
@Environment(EnvType.CLIENT)
public final class KeybindManager {
	private KeybindManager() {}

	private static final int MAX_SLOTS = KeybindPool.MAX_SLOTS;
	/** Whether each slot was down on the last tick, so the release edge can be reported. */
	private static final boolean[] wasDown = new boolean[MAX_SLOTS];

	private static final KeyMapping[] pool = new KeyMapping[MAX_SLOTS];
	private static final Set<Integer> claimedSlots = ConcurrentHashMap.newKeySet();
	/** The slot the server asked us to rebind, or -1: the next key pressed goes here. */
	private static volatile int rebinding = -1;

	/** Register the pool. Call once from client mod init, never later. */
	public static void init() {
		// The server names keys by number and cannot see this table, so a snapshot that
		// renumbered it would put every mod's default on the wrong key without a word.
		if (KeybindApi.letter('G') != InputConstants.KEY_G || KeybindApi.letter('B') != InputConstants.KEY_B) {
			Pandorical.LOGGER.error("InputConstants no longer numbers keys the way KeybindApi.letter does;"
				+ " every keybind default will land on the wrong key");
		}
		KeyMapping.Category category = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath(Pandorical.MOD_ID, "pandorical"));
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
		// The server can name a key but not read one: the binding is a line in this player's
		// options and nowhere else, so the mods menu learns it here or not at all.
		sendBindings();
	}

	/** The keybinds whose default key this client has already put on, one {@code id@slot} to a line. */
	private static final int MOST_REMEMBERED = 512;
	private static final Path DEFAULTS_APPLIED = FabricLoader.getInstance()
		.getConfigDir().resolve("pandorical").resolve("keybind-defaults.txt");

	/**
	 * Put on the keys the server's keybinds asked to start on, where nobody has chosen one.
	 *
	 * <p>Once per keybind and slot: {@code id@slot} goes in a file here the first time it is seen,
	 * whether or not the slot was free, so a key the player clears or moves afterwards is theirs
	 * and is never put back. A slot the player had already bound keeps their key. A keybind the
	 * server moves to another slot is a new binding and gets its default once more.
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
			// Every server's ids land here, so the oldest go once there are more than any one
			// player's servers could want remembered.
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
			sendBindings();
		}
	}

	/** Tell the server what each pool slot is bound to now, as this client's controls screen says. */
	public static void sendBindings() {
		if (!ClientPlayNetworking.canSend(KeybindBindingsC2S.TYPE)) return;
		List<String> keys = new ArrayList<>(MAX_SLOTS);
		for (int i = 0; i < MAX_SLOTS; i++) {
			keys.add(pool[i] == null || pool[i].isUnbound() ? "" : pool[i].getTranslatedKeyMessage().getString());
		}
		ClientPlayNetworking.send(new KeybindBindingsC2S(keys));
	}

	/** The server asks for this slot to take the next key pressed; a negative slot calls it off. */
	public static void handleRebindRequest(int slot) {
		rebinding = slot >= 0 && slot < MAX_SLOTS ? slot : -1;
	}

	/**
	 * Take this key press as the new binding, if one was asked for. True when the press was
	 * spent here and must go no further, which is the whole point: the key being bound is
	 * usually a key that does something else on the screen it was pressed on.
	 *
	 * <p>Escape clears the binding, as it does in the controls screen; every other key, including
	 * one already used elsewhere, is taken. Two things on one key is the player's to sort out, and
	 * refusing it here would be the one place in the game that does.
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
	 * The pooled mapping for a slot, or null if the slot is out of range.
	 *
	 * <p>Exposed so an input source that is not the keyboard can drive a pooled
	 * keybind the same way a key does. {@link #tick} reads presses through
	 * {@code consumeClick}, so anything that makes the mapping report a click
	 * reaches the server by the ordinary path and needs no separate protocol.
	 */
	public static KeyMapping poolMapping(int slot) {
		return slot >= 0 && slot < MAX_SLOTS ? pool[slot] : null;
	}

	/** Forward pool presses for claimed slots; drain unclaimed clicks so they cannot pile up. */
	public static void tick(Minecraft client) {
		if (pool[0] == null) return;
		for (int i = 0; i < MAX_SLOTS; i++) {
			while (pool[i].consumeClick()) {
				if (claimedSlots.contains(i) && ClientPlayNetworking.canSend(KeyPressC2S.TYPE)) {
					ClientPlayNetworking.send(new KeyPressC2S(i));
				}
			}
			// The other edge. A held key is a press followed, some ticks later, by this.
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
