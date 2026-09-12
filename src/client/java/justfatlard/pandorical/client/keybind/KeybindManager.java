package justfatlard.pandorical.client.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import justfatlard.pandorical.Pandorical;
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

/**
 * The client half of the pooled keybind capability: a fixed pool of real,
 * rebindable KeyMappings registered at normal client startup (the only time
 * the options system accepts them), whose meaning is assigned per server.
 *
 * <p>Slot 1 defaults to G, the rest start unbound; all live under the
 * "Pandorical" controls category with shipped default names ("Pandorical
 * Action N") that a server's synced lang overrides for its claimed slots.
 * Presses are only forwarded for slots the current server declared, so
 * unclaimed keys are inert.
 */
@Environment(EnvType.CLIENT)
public final class KeybindManager {
	private KeybindManager() {}

	/**
	 * Must match KeybindApiImpl.MAX_SLOTS and its POOL_DEFAULT_KEYS. Key codes
	 * are this snapshot's InputConstants table (NOT GLFW: KEY_G is 10 here,
	 * 71 is scroll lock); 0 is the unbound/unknown keyboard code.
	 */
	private static final int MAX_SLOTS = 8;
	private static final int[] POOL_DEFAULT_KEYS = {InputConstants.KEY_G, InputConstants.KEY_B, 0, 0, 0, 0, 0, 0};
	/** Whether each slot was down on the last tick, so the release edge can be reported. */
	private static final boolean[] wasDown = new boolean[MAX_SLOTS];

	private static final KeyMapping[] pool = new KeyMapping[MAX_SLOTS];
	private static final Set<Integer> claimedSlots = ConcurrentHashMap.newKeySet();
	/** The slot the server asked us to rebind, or -1: the next key pressed goes here. */
	private static volatile int rebinding = -1;

	/** Register the pool. Call once from client mod init, never later. */
	public static void init() {
		KeyMapping.Category category = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath(Pandorical.MOD_ID, "pandorical"));
		for (int i = 0; i < MAX_SLOTS; i++) {
			pool[i] = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.pandorical.action" + (i + 1), POOL_DEFAULT_KEYS[i], category));
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

	/** The keybinds whose default key this client has already put on, one id to a line. */
	private static final java.nio.file.Path DEFAULTS_APPLIED = net.fabricmc.loader.api.FabricLoader.getInstance()
		.getConfigDir().resolve("pandorical").resolve("keybind-defaults.txt");

	/**
	 * Put on the keys the server's keybinds asked to start on, where nobody has chosen one.
	 *
	 * <p>Once per keybind, ever: the id goes in a file here the first time it is seen, whether or
	 * not its slot was free, so a key the player clears or moves afterwards is theirs and is never
	 * put back. A slot the player had already bound keeps their key.
	 */
	public static void applyDefaults(justfatlard.pandorical.protocol.KeybindDefaultsS2C payload) {
		Set<String> applied = new java.util.LinkedHashSet<>();
		try {
			if (java.nio.file.Files.exists(DEFAULTS_APPLIED)) {
				for (String line : java.nio.file.Files.readAllLines(DEFAULTS_APPLIED)) {
					if (!line.isBlank()) applied.add(line.trim());
				}
			}
		} catch (java.io.IOException e) {
			Pandorical.LOGGER.warn("Could not read {}: {}", DEFAULTS_APPLIED, e.toString());
			return;
		}

		boolean bound = false;
		boolean remembered = false;
		for (var entry : payload.entries()) {
			if (entry.slot() < 0 || entry.slot() >= MAX_SLOTS || pool[entry.slot()] == null) continue;
			if (!applied.add(entry.id())) continue;
			remembered = true;
			if (!pool[entry.slot()].isUnbound()) continue;
			pool[entry.slot()].setKey(InputConstants.Type.KEYBOARD.getOrCreate(entry.key()));
			bound = true;
			Pandorical.LOGGER.info("Bound {} to its default key", entry.id());
		}

		if (remembered) {
			try {
				java.nio.file.Files.createDirectories(DEFAULTS_APPLIED.getParent());
				java.nio.file.Files.write(DEFAULTS_APPLIED, applied);
			} catch (java.io.IOException e) {
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
		java.util.List<String> keys = new java.util.ArrayList<>(MAX_SLOTS);
		for (int i = 0; i < MAX_SLOTS; i++) {
			keys.add(pool[i] == null || pool[i].isUnbound()
				? "Not bound" : pool[i].getTranslatedKeyMessage().getString());
		}
		ClientPlayNetworking.send(new KeybindBindingsC2S(keys));
	}

	/** The server asks for this slot to take the next key pressed; a negative slot calls it off. */
	public static void handleRebindRequest(int slot) {
		rebinding = slot >= 0 && slot < MAX_SLOTS ? slot : -1;
	}

	public static boolean isRebinding() {
		return rebinding >= 0;
	}

	/**
	 * Take this key press as the new binding, if one was asked for. True when the press was
	 * spent here and must go no further, which is the whole point: the key being bound is
	 * usually a key that does something else on the screen it was pressed on.
	 *
	 * <p>Escape leaves the binding alone, the way the controls screen does; every other key,
	 * including one already used elsewhere, is taken. Two things on one key is the player's to
	 * sort out, and refusing it here would be the one place in the game that does.
	 */
	public static boolean captureKey(net.minecraft.client.input.KeyEvent event) {
		int slot = rebinding;
		if (slot < 0) return false;
		rebinding = -1;
		Minecraft client = Minecraft.getInstance();
		if (event.key() != InputConstants.KEY_ESCAPE && pool[slot] != null) {
			pool[slot].setKey(InputConstants.getKey(event));
			KeyMapping.resetMapping();
			if (client != null && client.options != null) client.options.save();
		}
		sendBindings();
		return true;
	}

	/** How many slots the pool has. */
	public static int poolSize() {
		return MAX_SLOTS;
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

	/** Whether the current server declared this slot; unclaimed presses go nowhere. */
	public static boolean isClaimed(int slot) {
		return claimedSlots.contains(slot);
	}

	/** Forward pool presses for claimed slots; drain unclaimed clicks so they cannot pile up. */
	public static void tick(Minecraft client) {
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
