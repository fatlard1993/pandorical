package justfatlard.pandorical.keybind;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.api.Capabilities;
import justfatlard.pandorical.api.KeybindApi;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.protocol.KeybindDeclarationsS2C;
import justfatlard.pandorical.protocol.KeybindDefaultsS2C;
import justfatlard.pandorical.protocol.KeybindRebindS2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class KeybindPool implements KeybindApi {
	public static final KeybindPool INSTANCE = new KeybindPool();

	/**
	 * Sixteen, which is as many as the protocol carries: the bindings a client reports back are a
	 * list capped at sixteen by its own codec, so a seventeenth slot could be claimed and never
	 * heard about. It was eight, and seven of them were spoken for.
	 */
	public static final int MAX_SLOTS = 16;
	private static final int UNBOUND = 0;

	/**
	 * Only the first two arrive bound. Everything past them is unbound on purpose: a pool that
	 * shipped sixteen pre-bound keys would take sixteen keys off the player whether any mod wanted
	 * them or not, and a mod that wants a particular key asks for it through
	 * {@link KeybindApi#bindByDefault}.
	 */
	private static final int[] POOL_DEFAULT_KEYS = {KeybindApi.letter('G'), KeybindApi.letter('B'),
		UNBOUND, UNBOUND, UNBOUND, UNBOUND, UNBOUND, UNBOUND,
		UNBOUND, UNBOUND, UNBOUND, UNBOUND, UNBOUND, UNBOUND, UNBOUND, UNBOUND};

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
		warnIfClientPoolIsShort(player, keys.size());
		List<String> now = List.copyOf(keys);
		List<String> before = bindings.put(player.getUUID(), now);
		boolean changed = !now.equals(before);
		for (BindingsListener listener : bindingsListeners) listener.bindingsReported(player, changed);
	}

	/**
	 * A client older than the pool it is talking to reports fewer slots than it is being asked
	 * about, and the keybinds past its end never fire. That is a silently absent feature,
	 * which is the worst kind, so it is said out loud - once per player, because the client reports
	 * its bindings again every time they change.
	 */
	private final Set<UUID> warnedShortPool = ConcurrentHashMap.newKeySet();

	private void warnIfClientPoolIsShort(ServerPlayer player, int reportedSlots) {
		int highestClaimed = -1;
		for (int slot : bySlot.keySet()) highestClaimed = Math.max(highestClaimed, slot);
		if (reportedSlots > highestClaimed || !warnedShortPool.add(player.getUUID())) return;

		Registration lost = bySlot.get(highestClaimed);
		Pandorical.LOGGER.warn(
			"{}'s client has {} keybind slots but this server claims up to {} ({}): keybinds above"
			+ " its pool will never fire. Update Pandorical on that client.",
			player.getName().getString(), reportedSlots, highestClaimed + 1,
			lost == null ? "unknown" : lost.id());
	}

	/** Ask this player's client to bind the next key it sees to this slot. */
	public void requestRebind(ServerPlayer player, int slot) {
		if (!canRebind(player)) return;
		ServerPlayNetworking.send(player, new KeybindRebindS2C(slot));
	}

	/**
	 * Whether this player's client can be asked to rebind. The capability predates the channel, so
	 * a client from before rebinding declares one and cannot receive the other.
	 */
	public static boolean canRebind(ServerPlayer player) {
		return PandoricalApi.hasCapability(player, Capabilities.KEYBINDS)
			&& ServerPlayNetworking.canSend(player, KeybindRebindS2C.TYPE);
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
		warnedShortPool.remove(playerUuid);
	}
}
