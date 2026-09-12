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

/**
 * The fixed pool of keybind slots every Pandorical client registers, and which mod has claimed
 * each. Pool model rationale in the {@link KeybindApi} javadoc.
 */
public final class KeybindPool implements KeybindApi {
	public static final KeybindPool INSTANCE = new KeybindPool();

	/** The pool the client registers at startup; the client reads its size and defaults from here. */
	public static final int MAX_SLOTS = 8;
	/** What an entry in {@link #POOL_DEFAULT_KEYS} says when the slot starts unbound. */
	private static final int UNBOUND = 0;

	// Two slots come pre-bound. A registration that names a slot's key gets that slot; see
	// chooseSlot for why the others do not.
	private static final int[] POOL_DEFAULT_KEYS = {KeybindApi.letter('G'), KeybindApi.letter('B'),
		UNBOUND, UNBOUND, UNBOUND, UNBOUND, UNBOUND, UNBOUND};

	/** The key a pool slot starts bound to, or 0 for none. */
	public static int poolDefaultKey(int slot) {
		return POOL_DEFAULT_KEYS[slot];
	}
	private static final int MAX_PRESSES_PER_TICK = 8;

	private record Registration(String id, String displayName, KeybindHandler handler, int preferredKey) {}

	/** Keybinds that asked for their preferred key to be bound on clients by default. */
	private final Set<String> boundByDefault = ConcurrentHashMap.newKeySet();

	/** A claimed slot, as the mods menu shows it: which mod, what it is called, where it sits. */
	public record Claim(int slot, String id, String displayName) {}

	private final Map<Integer, Registration> bySlot = new ConcurrentHashMap<>();
	/** What each player's client says its pool keys are bound to, by slot; empty until it says. */
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

		// The controls screen label for the claimed slot resolves through
		// the synced pandorical lang, overriding the client's shipped
		// "Pandorical Action N" default for this server only
		PandoricalApi.contentRegistry().addLangEntries(Map.of("key.pandorical.action" + (slot + 1), displayName));

		Pandorical.LOGGER.info(
			"Keybind registered: '{}' -> slot {} (\"{}\", pool default {})",
			id, slot, displayName, POOL_DEFAULT_KEYS[slot] == UNBOUND ? "unbound" : POOL_DEFAULT_KEYS[slot]);
	}

	/**
	 * Pick a slot for a registration, without giving away a key somebody else asked for.
	 *
	 * <p>A slot that carries a pool default is the only kind a player finds already bound, so
	 * it is the only kind worth competing for - and handing it to the first mod to ask for
	 * anything at all made the allocation depend on mod load order. Two mods, one of them
	 * naming the default key explicitly, and which one got it came down to which initialised
	 * first: the mod that wanted G got an unbound slot and did nothing, while the mod that
	 * wanted something else answered G.
	 *
	 * <p>So a defaulted slot now goes only to a registration that asked for that default.
	 * Everything else takes an unbound one, and the pre-bound key stays with whoever named it
	 * however the loader happens to order the mods that day.
	 */
	private int chooseSlot(int preferredDefaultKey) {
		for (int i = 0; i < MAX_SLOTS; i++) {
			if (!bySlot.containsKey(i) && POOL_DEFAULT_KEYS[i] == preferredDefaultKey) return i;
		}
		for (int i = 0; i < MAX_SLOTS; i++) {
			if (!bySlot.containsKey(i) && POOL_DEFAULT_KEYS[i] == UNBOUND) return i;
		}

		// Every unbound slot is spoken for. Taking a defaulted one now is still better than
		// refusing to register at all, but it is worth saying out loud, because the mod that
		// wanted that key is about to find something else answering it.
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

	/** Every slot some mod has claimed, in pool order. */
	public List<Claim> claims() {
		List<Claim> out = new ArrayList<>();
		for (int slot = 0; slot < MAX_SLOTS; slot++) {
			Registration registration = bySlot.get(slot);
			if (registration != null) out.add(new Claim(slot, registration.id(), registration.displayName()));
		}
		return out;
	}

	/** The claims whose id is namespaced to this mod. */
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

	/** Hears each report of a player's pool key bindings, and whether it differs from their last. */
	@FunctionalInterface
	public interface BindingsListener {
		void bindingsReported(ServerPlayer player, boolean changed);
	}

	private final List<BindingsListener> bindingsListeners = new CopyOnWriteArrayList<>();

	/** Run this listener on every bindings report from here on. */
	public void onBindingsReported(BindingsListener listener) {
		bindingsListeners.add(listener);
	}

	/** @hidden the client reporting what its pool keys are bound to. */
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

	/** @hidden push claimed slots after the capability handshake completes. */
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

	/** @hidden validate and dispatch one press; called on the server thread. */
	public void handleKeyPress(ServerPlayer player, int slot) {
		if (!PandoricalApi.hasCapability(player, Capabilities.KEYBINDS)) return;
		if (slot < 0 || slot >= MAX_SLOTS) return;
		Registration registration = bySlot.get(slot);
		if (registration == null) return;

		// A held or spammed key must not become a server-side amplifier
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

	/** @hidden validate and dispatch one release; called on the server thread. */
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
