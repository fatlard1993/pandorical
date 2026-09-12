package justfatlard.pandorical.screen;

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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class ScreenRegistry implements ScreenApi {
	public static final ScreenRegistry INSTANCE = new ScreenRegistry();

	private static final int MAX_ACTION_DATA_ENTRIES = 32;
	private static final int MAX_ACTION_STRING_LENGTH = 1024;

	private record ScreenContext(String screenType, String screenId) {}

	private final Map<UUID, ScreenContext> playerScreens = new ConcurrentHashMap<>();

	private final Map<String, Map<String, BiConsumer<ServerPlayer, Map<String, String>>>> actionHandlers = new ConcurrentHashMap<>();
	private final Map<String, BiConsumer<ServerPlayer, Map<String, String>>> fallbackHandlers = new ConcurrentHashMap<>();
	private final Map<String, Consumer<ServerPlayer>> closeHandlers = new ConcurrentHashMap<>();
	private final Map<String, ScreenApi.SlotChangeHandler> slotChangeHandlers = new ConcurrentHashMap<>();
	private final Map<String, ScreenApi.PlaceRecipeHandler> placeRecipeHandlers = new ConcurrentHashMap<>();
	private final Map<String, Consumer<ServerPlayer>> containerRemovedHandlers = new ConcurrentHashMap<>();

	private ScreenRegistry() {}

	public String openScreenId(UUID playerUuid) {
		return getPlayerScreenId(playerUuid);
	}

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
	 * Only while {@code screenId} is still the open one: {@code openMenu} closes the previous
	 * container after the incoming screen has registered, so the old removed-callback runs while
	 * {@link #playerScreens} holds the new screen. Screen ids are unique per open.
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
		// Close first, or the old screen's removed-handler runs against the new screen's
		// per-player state (see clearPlayerScreen(UUID, String)).
		if (player.containerMenu instanceof PandoricalMenu) {
			player.closeContainer();
		}

		setPlayerScreen(player.getUUID(), screen.screenType(), screen.screenId());

		// Before openMenu: the client matches the incoming menu against this definition.
		ServerPlayNetworking.send(player, screen);

		String screenType = screen.screenType();
		String openedScreenId = screen.screenId();
		int slotCount = screen.container().map(c -> c.slotCount()).orElse(0);
		player.openMenu(new PandoricalMenuProvider(screen, serverContainer, readOnlySlots,
			() -> {
				SlotChangeHandler handler = slotChangeHandlers.get(screenType);
				if (handler != null) {
					for (int i = 0; i < slotCount; i++) {
						handler.onSlotChange(player, i, serverContainer.getItem(i));
					}
				}
			},
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

		// The client silently drops an update for any other screen id.
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
		// A screen opened since must keep its tracking.
		String currentId = getPlayerScreenId(player.getUUID());
		if (screenId.equals(currentId)) {
			// Close the server-side menu too, or its removed-callback never runs and its items
			// are stranded. removed() clears the tracking.
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

		// A reserved id owned by no component, so checked before the component handlers.
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
