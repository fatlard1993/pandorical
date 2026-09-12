package justfatlard.pandorical.screen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.api.Capabilities;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.PandoricalMenuProvider;
import justfatlard.pandorical.api.ScreenApi;
import justfatlard.pandorical.protocol.CloseScreenS2C;
import justfatlard.pandorical.protocol.ComponentUpdate;
import justfatlard.pandorical.protocol.OpenScreenS2C;
import justfatlard.pandorical.protocol.ScreenActionC2S;
import justfatlard.pandorical.protocol.UpdateScreenS2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

/** Every declarative screen's handlers, which screen each player has open, and where an action goes. */
public final class ScreenRegistry implements ScreenApi {
	public static final ScreenRegistry INSTANCE = new ScreenRegistry();

	private static final int MAX_ACTION_DATA_ENTRIES = 32;
	private static final int MAX_ACTION_STRING_LENGTH = 1024;

	/** Holds the type and ID of the screen currently open for a player. */
	private record ScreenContext(String screenType, String screenId) {}

	private final Map<UUID, ScreenContext> playerScreens = new ConcurrentHashMap<>();

	private final Map<String, Map<String, BiConsumer<ServerPlayer, Map<String, String>>>> actionHandlers = new ConcurrentHashMap<>();
	private final Map<String, BiConsumer<ServerPlayer, Map<String, String>>> fallbackHandlers = new ConcurrentHashMap<>();
	private final Map<String, Consumer<ServerPlayer>> closeHandlers = new ConcurrentHashMap<>();
	private final Map<String, ScreenApi.SlotChangeHandler> slotChangeHandlers = new ConcurrentHashMap<>();
	private final Map<String, ScreenApi.PlaceRecipeHandler> placeRecipeHandlers = new ConcurrentHashMap<>();
	private final Map<String, Consumer<ServerPlayer>> containerRemovedHandlers = new ConcurrentHashMap<>();

	private ScreenRegistry() {}

	/** The id of the screen this player has open through Pandorical, or null for none. */
	public String openScreenId(UUID playerUuid) {
		return getPlayerScreenId(playerUuid);
	}

	/** A player gone from the server has no screen open. */
	public void forgetPlayer(UUID playerUuid) {
		playerScreens.remove(playerUuid);
	}

	private void setPlayerScreen(UUID playerUuid, String screenType, String screenId) {
		playerScreens.put(playerUuid, new ScreenContext(screenType, screenId));
	}

	private String getPlayerScreenType(UUID playerUuid) {
		ScreenContext ctx = playerScreens.get(playerUuid);
		return ctx != null ? ctx.screenType() : null;
	}

	private String getPlayerScreenId(UUID playerUuid) {
		ScreenContext ctx = playerScreens.get(playerUuid);
		return ctx != null ? ctx.screenId() : null;
	}

	private void clearPlayerScreen(UUID playerUuid) {
		playerScreens.remove(playerUuid);
	}

	/**
	 * Forget this player's screen, but only when it is still the one being torn down.
	 *
	 * <p>{@code openMenu} closes the previous container <em>after</em> the incoming
	 * screen has already registered, so the old container's removed-callback runs
	 * while {@link #playerScreens} holds the new screen. Removing unconditionally
	 * there erases that registration, and because {@code handleAction} returns
	 * immediately when a player has no screen, every later click on the screen the
	 * player is looking at is dropped in silence.
	 *
	 * <p>Screen ids are per-open (a random UUID from the builder), so comparing them
	 * is what separates "this screen closed" from "a newer one replaced it".
	 */
	private void clearPlayerScreen(UUID playerUuid, String screenId) {
		playerScreens.computeIfPresent(playerUuid,
			(uuid, ctx) -> ctx.screenId().equals(screenId) ? null : ctx);
	}

	@Override
	public void open(ServerPlayer player, OpenScreenS2C screen) {
		if (!PandoricalApi.hasCapability(player, Capabilities.SCREENS)) {
			Pandorical.LOGGER.debug("Cannot open screen for {} — client lacks 'screens' capability",
				player.getName().getString());
			return;
		}
		if (screen.container().isPresent()) {
			Pandorical.LOGGER.warn(
				"Screen '{}' has a container definition but was opened with open() instead of openContainer() — " +
				"container slots will not work. Use openContainer() for screens with inventory slots.",
				screen.screenType());
		}
		setPlayerScreen(player.getUUID(), screen.screenType(), screen.screenId());
		ServerPlayNetworking.send(player, screen);
	}

	@Override
	public void openContainer(ServerPlayer player, OpenScreenS2C screen,
			Container serverContainer, Set<Integer> readOnlySlots) {
		if (!PandoricalApi.hasCapability(player, Capabilities.SCREENS)) {
			Pandorical.LOGGER.debug("Cannot open container for {} — client lacks 'screens' capability",
				player.getName().getString());
			return;
		}
		// Tear down whatever is already open before registering this screen.
		//
		// openMenu closes the current container itself, but it does that *after*
		// the new screen has registered, so the outgoing screen's removed-handler
		// runs while this player's state already describes the incoming one. A
		// consumer that keeps per-player state then cleans up the session it just
		// created: player-trade cancels the new trade and returns its items,
		// village-mail returns the new screen's attachment, fletch-craft empties
		// the new grid. Closing first means every teardown sees its own state.
		//
		// Guarded on our own menu type so an unrelated vanilla container is never
		// closed out from under the player.
		if (player.containerMenu instanceof PandoricalMenu) {
			player.closeContainer();
		}

		setPlayerScreen(player.getUUID(), screen.screenType(), screen.screenId());

		// The screen definition must be sent before openMenu: the client stores it in
		// a pending map and matches the incoming menu against it.
		ServerPlayNetworking.send(player, screen);

		String screenType = screen.screenType();
		// Captured so the removed-callback can tell its own teardown from being
		// replaced by a newer screen; see clearPlayerScreen(UUID, String).
		String openedScreenId = screen.screenId();
		int slotCount = screen.container().map(c -> c.slotCount()).orElse(0);
		player.openMenu(new PandoricalMenuProvider(screen, serverContainer, readOnlySlots,
			// slot change callback (reports every slot, not just changed ones)
			() -> {
				SlotChangeHandler handler = slotChangeHandlers.get(screenType);
				if (handler != null) {
					for (int i = 0; i < slotCount; i++) {
						handler.onSlotChange(player, i, serverContainer.getItem(i));
					}
				}
			},
			// removed callback
			() -> {
				Consumer<ServerPlayer> handler = containerRemovedHandlers.get(screenType);
				if (handler != null) handler.accept(player);
				clearPlayerScreen(player.getUUID(), openedScreenId);
			}
		));
	}

	@Override
	public void update(ServerPlayer player, String screenId, List<ComponentUpdate> updates) {
		if (!PandoricalApi.isAvailable(player)) return;

		// The client matches updates on the screen ID, and drops anything else where it
		// lands. That silence is the trap: the usual mistake is addressing an update by the
		// screen TYPE, which is the constant a mod actually has on hand - ScreenBuilder mints
		// the id itself, so the two are never equal unless id() was called. The feature then
		// works perfectly on the server and never redraws, with nothing anywhere to say why.
		String open = getPlayerScreenId(player.getUUID());
		if (open != null && !open.equals(screenId)) {
			Pandorical.LOGGER.warn(
				"Screen update addressed to '{}' but {} has screen id '{}' open (type '{}') — "
					+ "the client would drop this. Pass the id from ScreenBuilder.screenId(), "
					+ "not the screen type.",
				screenId, player.getName().getString(), open,
				getPlayerScreenType(player.getUUID()));
			return;
		}

		ServerPlayNetworking.send(player, new UpdateScreenS2C(screenId, updates));
	}

	@Override
	public void close(ServerPlayer player, String screenId) {
		if (!PandoricalApi.isAvailable(player)) return;
		// Only act when this screenId is still the active one.
		// If handleResponse() opened a NEW screen before we got here, the new
		// screen's tracking must survive so its buttons can be handled.
		String currentId = getPlayerScreenId(player.getUUID());
		if (screenId.equals(currentId)) {
			// For a container screen, close the server-side menu too. Otherwise the menu
			// stays live after the client is told to hide the overlay, its removed-callback
			// never runs, and any items held in the container are stranded (and destroyed on
			// the eventual real close). The instanceof guard ensures we only ever close our
			// own menu, never an unrelated vanilla one; removed() clears tracking itself.
			if (player.containerMenu instanceof PandoricalMenu) {
				player.closeContainer();
			} else {
				clearPlayerScreen(player.getUUID());
			}
		}
		ServerPlayNetworking.send(player, new CloseScreenS2C(screenId));
	}

	@Override
	public void onAction(String screenType, String componentId, BiConsumer<ServerPlayer, Map<String, String>> handler) {
		actionHandlers.computeIfAbsent(screenType, k -> new ConcurrentHashMap<>()).put(componentId, handler);
	}

	@Override
	public void onClose(String screenType, Consumer<ServerPlayer> handler) {
		closeHandlers.put(screenType, handler);
	}

	@Override
	public void onActionFallback(String screenType, BiConsumer<ServerPlayer, Map<String, String>> handler) {
		fallbackHandlers.put(screenType, handler);
	}

	@Override
	public void onSlotChange(String screenType, SlotChangeHandler handler) {
		slotChangeHandlers.put(screenType, handler);
	}

	@Override
	public void onPlaceRecipe(String screenType, PlaceRecipeHandler handler) {
		placeRecipeHandlers.put(screenType, handler);
	}

	@Override
	public void onContainerRemoved(String screenType, Consumer<ServerPlayer> handler) {
		containerRemovedHandlers.put(screenType, handler);
	}

	public void handleAction(ServerPlayer player, ScreenActionC2S action) {
		String screenType = getPlayerScreenType(player.getUUID());
		if (screenType == null) return;

		String expectedScreenId = getPlayerScreenId(player.getUUID());
		if (expectedScreenId != null && !expectedScreenId.equals(action.screenId())) {
			Pandorical.LOGGER.warn(
				"Player {} sent action for screen '{}' but has screen '{}' open — ignoring",
				player.getName().getString(), action.screenId(), expectedScreenId);
			return;
		}

		if (action.data().size() > MAX_ACTION_DATA_ENTRIES) {
			Pandorical.LOGGER.warn(
				"Player {} sent action with {} data entries (max {}) — ignoring",
				player.getName().getString(), action.data().size(), MAX_ACTION_DATA_ENTRIES);
			return;
		}
		for (var entry : action.data().entrySet()) {
			if (entry.getKey().length() > MAX_ACTION_STRING_LENGTH || entry.getValue().length() > MAX_ACTION_STRING_LENGTH) {
				Pandorical.LOGGER.warn(
					"Player {} sent action with oversized data — ignoring",
					player.getName().getString());
				return;
			}
		}

		if ("close".equals(action.action())) {
			Consumer<ServerPlayer> closeHandler = closeHandlers.get(screenType);
			if (closeHandler != null) closeHandler.accept(player);
			clearPlayerScreen(player.getUUID());
			return;
		}

		// The reserved ask a recipe book sends, before component handlers: no screen owns a
		// component by this name, and a station should not have to register one to be filled.
		if (ScreenApi.PLACE_RECIPE_COMPONENT.equals(action.componentId())) {
			PlaceRecipeHandler placer = placeRecipeHandlers.get(screenType);
			if (placer == null) return;

			int displayIndex;
			try {
				displayIndex = Integer.parseInt(
					action.data().getOrDefault(ScreenApi.PLACE_RECIPE_DATA_RECIPE, ""));
			} catch (NumberFormatException e) {
				return;
			}

			var server = player.level().getServer();
			if (server == null) return;
			var info = server.getRecipeManager().getRecipeFromDisplay(new RecipeDisplayId(displayIndex));
			if (info == null) return;

			placer.placeRecipe(player, info.parent(),
				Boolean.parseBoolean(action.data().get(ScreenApi.PLACE_RECIPE_DATA_ALL)));
			return;
		}

		Map<String, BiConsumer<ServerPlayer, Map<String, String>>> handlers = actionHandlers.get(screenType);
		if (handlers != null) {
			BiConsumer<ServerPlayer, Map<String, String>> handler = handlers.get(action.componentId());
			if (handler != null) {
				handler.accept(player, action.data());
				return;
			}
		}

		// Fallback handler for dynamic component IDs
		BiConsumer<ServerPlayer, Map<String, String>> fallback = fallbackHandlers.get(screenType);
		if (fallback != null) {
			Map<String, String> dataWithId = new HashMap<>(action.data());
			dataWithId.put(ScreenApi.FALLBACK_COMPONENT_ID_KEY, action.componentId());
			fallback.accept(player, dataWithId);
		} else {
			Pandorical.LOGGER.debug(
				"Unhandled screen action: screen='{}' component='{}' action='{}'",
				screenType, action.componentId(), action.action());
		}
	}
}
