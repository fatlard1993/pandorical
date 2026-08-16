package justfatlard.pandorical.client.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.protocol.KeyPressC2S;
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
	private static final int[] POOL_DEFAULT_KEYS = {InputConstants.KEY_G, 0, 0, 0, 0, 0, 0, 0};

	private static final KeyMapping[] pool = new KeyMapping[MAX_SLOTS];
	private static final Set<Integer> claimedSlots = ConcurrentHashMap.newKeySet();

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
		}
	}

	public static void clear() {
		claimedSlots.clear();
	}
}
