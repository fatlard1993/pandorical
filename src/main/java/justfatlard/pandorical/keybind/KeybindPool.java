package justfatlard.pandorical.keybind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.api.Capabilities;
import justfatlard.pandorical.api.KeybindApi;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.protocol.KeybindDeclarationsS2C;
import justfatlard.pandorical.protocol.KeybindDefaultsS2C;
import justfatlard.pandorical.protocol.KeybindRebindS2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class KeybindPool implements KeybindApi {
	public static final KeybindPool INSTANCE = new KeybindPool();

	public static final int MAX_SLOTS = 8;
	private static final int UNBOUND = 0;

	private static final int[] POOL_DEFAULT_KEYS = {KeybindApi.letter('G'), KeybindApi.letter('B'),
		UNBOUND, UNBOUND, UNBOUND, UNBOUND, UNBOUND, UNBOUND};

	public static int poolDefaultKey(int slot) {
		return POOL_DEFAULT_KEYS[slot];
	}
	private static final int MAX_PRESSES_PER_TICK = 8;

	private record Registration(String id, String displayName, KeybindHandler handler, int preferredKey) {}

	private final Set<String> boundByDefault = ConcurrentHashMap.newKeySet();

	public record Claim(int slot, String id, String displayName) {}

	private final Map<Integer, Registration> bySlot = new ConcurrentHashMap<>();
	private final Map<UUID, List<String>> bindings = new ConcurrentHashMap<>();
	private final Set<String> registeredIds = ConcurrentHashMap.newKeySet();
	// Per-player rate limit: [tick the count belongs to, dispatches that tick]
	private final Map<UUID, long[]> pressCounters = new ConcurrentHashMap<>();

	private KeybindPool() {}

	@Override
	public void register(String id, int preferredDefaultKey, String displayName, KeybindHandler handler) {
		if (id == null || displayName == null || handler == null) {
			Pandorical.LOGGER.warn("Ignoring keybind registration with null id/name/handler");
			return;
		}
		if (!registeredIds.add(id)) {
			Pandorical.LOGGER.warn("Keybind id '{}' already registered — ignoring", id);
			return;
		}

		int slot = chooseSlot(preferredDefaultKey);
		if (slot < 0) {
			registeredIds.remove(id);
			Pandorical.LOGGER.error(
				"Keybind pool exhausted ({} slots) — cannot register '{}'", MAX_SLOTS, id);
			return;
		}
		bySlot.put(slot, new Registration(id, displayName, handler, preferredDefaultKey));

		// Overrides the client's shipped "Pandorical Action N" label, for this server only.
		PandoricalApi.contentRegistry().addLangEntries(Map.of("key.pandorical.action" + (slot + 1), displayName));

		Pandorical.LOGGER.info(
			"Keybind registered: '{}' -> slot {} (\"{}\", pool default {})",
			id, slot, displayName, POOL_DEFAULT_KEYS[slot] == UNBOUND ? "unbound" : POOL_DEFAULT_KEYS[slot]);
	}

	/**
	 * A pre-bound slot goes only to a registration that asked for its key, while any unbound slot
	 * is left, so who gets a default key does not depend on mod load order.
	 */
	private int chooseSlot(int preferredDefaultKey) {
		for (int i = 0; i < MAX_SLOTS; i++) {
			if (!bySlot.containsKey(i) && POOL_DEFAULT_KEYS[i] == preferredDefaultKey) return i;
		}
		for (int i = 0; i < MAX_SLOTS; i++) {
			if (!bySlot.containsKey(i) && POOL_DEFAULT_KEYS[i] == UNBOUND) return i;
		}

		for (int i = 0; i < MAX_SLOTS; i++) {
			if (!bySlot.containsKey(i)) {
				Pandorical.LOGGER.warn(
					"Keybind pool has no unbound slot left: slot {} was pre-bound to key {} and"
					+ " is being given to a registration that did not ask for it", i,
					POOL_DEFAULT_KEYS[i]);
				return i;
			}
		}
		return -1;
	}

	@Override
	public void bindByDefault(String id) {
		if (!registeredIds.contains(id)) {
			Pandorical.LOGGER.warn("bindByDefault('{}'): no keybind registered by that id; call register('{}', ...) first", id, id);
			return;
		}
		boundByDefault.add(id);
	}

	public List<Claim> claims() {
		List<Claim> out = new ArrayList<>();
		for (int slot = 0; slot < MAX_SLOTS; slot++) {
			Registration registration = bySlot.get(slot);
			if (registration != null) out.add(new Claim(slot, registration.id(), registration.displayName()));
		}
		return out;
	}

	public List<Claim> claimsOf(String modId) {
		List<Claim> out = new ArrayList<>();
		for (Claim claim : claims()) {
			int colon = claim.id().indexOf(':');
			if (colon > 0 && claim.id().substring(0, colon).equals(modId)) out.add(claim);
		}
		return out;
	}

	/** What this player's client has this slot bound to, empty for nothing, or null when it has not said. */
	public String bindingOf(ServerPlayer player, int slot) {
		List<String> keys = bindings.get(player.getUUID());
		if (keys == null || slot < 0 || slot >= keys.size()) return null;
		String key = keys.get(slot);
		return key == null ? "" : key;
	}

	@FunctionalInterface
	public interface BindingsListener {
		void bindingsReported(ServerPlayer player, boolean changed);
	}

	private final List<BindingsListener> bindingsListeners = new CopyOnWriteArrayList<>();

	public void onBindingsReported(BindingsListener listener) {
		bindingsListeners.add(listener);
	}

	/** @hidden */
	public void handleBindings(ServerPlayer player, List<String> keys) {
		List<String> now = List.copyOf(keys);
		List<String> before = bindings.put(player.getUUID(), now);
		boolean changed = !now.equals(before);
		for (BindingsListener listener : bindingsListeners) listener.bindingsReported(player, changed);
	}

	/** Ask this player's client to bind the next key it sees to this slot. */
	public void requestRebind(ServerPlayer player, int slot) {
		if (!PandoricalApi.hasCapability(player, Capabilities.KEYBINDS)) return;
		ServerPlayNetworking.send(player, new KeybindRebindS2C(slot));
	}

	/** @hidden */
	public void handlePlayerReady(ServerPlayer player) {
		bindings.remove(player.getUUID());
		if (bySlot.isEmpty() || !PandoricalApi.hasCapability(player, Capabilities.KEYBINDS)) return;
		List<Integer> slots = new ArrayList<>(bySlot.keySet());
		Collections.sort(slots);
		ServerPlayNetworking.send(player, new KeybindDeclarationsS2C(slots));

		List<KeybindDefaultsS2C.Entry> defaults = new ArrayList<>();
		for (int slot : slots) {
			Registration registration = bySlot.get(slot);
			if (registration != null && registration.preferredKey() != UNBOUND
					&& boundByDefault.contains(registration.id())) {
				defaults.add(new KeybindDefaultsS2C.Entry(slot, registration.id(), registration.preferredKey()));
			}
		}
		if (!defaults.isEmpty() && ServerPlayNetworking.canSend(player, KeybindDefaultsS2C.TYPE)) {
			ServerPlayNetworking.send(player, new KeybindDefaultsS2C(defaults));
		}
	}

	/** @hidden Server thread only. */
	public void handleKeyPress(ServerPlayer player, int slot) {
		if (!PandoricalApi.hasCapability(player, Capabilities.KEYBINDS)) return;
		if (slot < 0 || slot >= MAX_SLOTS) return;
		Registration registration = bySlot.get(slot);
		if (registration == null) return;

		// A held or spammed key must not amplify server work.
		long currentTick = player.level().getServer().getTickCount();
		long[] counter = pressCounters.computeIfAbsent(player.getUUID(), u -> new long[]{-1, 0});
		if (counter[0] != currentTick) {
			counter[0] = currentTick;
			counter[1] = 0;
		}
		if (++counter[1] > MAX_PRESSES_PER_TICK) return;

		try {
			registration.handler().onPress(player);
		} catch (Exception e) {
			Pandorical.LOGGER.error(
				"Keybind handler '{}' threw for player {}: {}",
				registration.id(), player.getName().getString(), e.getMessage(), e);
		}
	}

	/** @hidden Server thread only. */
	public void handleKeyRelease(ServerPlayer player, int slot) {
		if (!PandoricalApi.hasCapability(player, Capabilities.KEYBINDS)) return;
		if (slot < 0 || slot >= MAX_SLOTS) return;
		Registration registration = bySlot.get(slot);
		if (registration == null) return;
		try {
			registration.handler().onRelease(player);
		} catch (Exception e) {
			Pandorical.LOGGER.error(
				"Keybind release handler '{}' threw for player {}: {}",
				registration.id(), player.getName().getString(), e.getMessage(), e);
		}
	}

	/** @hidden */
	public void removePlayer(UUID playerUuid) {
		bindings.remove(playerUuid);
		pressCounters.remove(playerUuid);
	}
}
