package justfatlard.pandorical.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Public API for server mods to interact with Pandorical.
 */
public final class PandoricalApi {
    private PandoricalApi() {}

    private static final ScreenApiImpl SCREENS = new ScreenApiImpl();
    private static final HudApiImpl HUD = new HudApiImpl();
    private static final justfatlard.pandorical.content.ContentRegistry CONTENT = new justfatlard.pandorical.content.ContentRegistry();
    private static final CameraApiImpl CAMERA = new CameraApiImpl();
    private static final PlayerInventoryApiImpl PLAYER_INVENTORY = new PlayerInventoryApiImpl();

    /** Holds the type and ID of the screen currently open for a player. */
    private record ScreenContext(String screenType, String screenId) {}

    // --- Per-player state ---
    private static final Map<UUID, Set<String>> playerCapabilities = new ConcurrentHashMap<>();
    private static final Set<UUID> contentReadyPlayers = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, ScreenContext> playerScreens = new ConcurrentHashMap<>();

    private static final int MAX_ACTION_DATA_ENTRIES = 32;
    private static final int MAX_ACTION_STRING_LENGTH = 1024;

    // --- Public API ---

    /** Returns true when Pandorical is loaded on the server. */
    public static boolean isAvailable() { return true; }

    /**
     * Returns true if the player has completed the Pandorical handshake; false for vanilla clients.
     * Use this to guard all Pandorical API calls so they are not sent to players without the mod.
     */
    public static boolean isAvailable(ServerPlayer player) {
        return playerCapabilities.containsKey(player.getUUID());
    }

    /**
     * Returns true if the player's Pandorical client advertised the given capability.
     * Known capability strings: {@code "screens"}, {@code "hud"}, {@code "camera"}.
     * A capability being absent means the client version does not support that feature.
     */
    public static boolean hasCapability(ServerPlayer player, String capability) {
        Set<String> caps = playerCapabilities.get(player.getUUID());
        return caps != null && caps.contains(capability);
    }

    /**
     * Check if a player's client has finished loading synced content (blocks, items, assets).
     * Returns true if the player has Pandorical and has sent ContentReadyC2S,
     * or if no content sync was needed.
     */
    public static boolean isContentReady(ServerPlayer player) {
        if (!isAvailable(player)) return false;
        // If there's no content to sync, the player is ready as soon as handshake completes
        if (!CONTENT.hasContent()) return true;
        return contentReadyPlayers.contains(player.getUUID());
    }

    /** Returns the screen API for opening, updating, and closing declarative screens. */
    public static ScreenApi screens() { return SCREENS; }
    /** Returns the HUD API for showing, updating, and hiding HUD overlays. */
    public static HudApi hud() { return HUD; }
    /** Returns the content API for registering custom blocks, items, and assets. */
    public static ContentApi content() { return CONTENT; }
    /** Returns the camera API for adjusting camera distance and perspective for a player. */
    public static CameraApi camera() { return CAMERA; }

    /**
     * Returns the player inventory API for registering extra inventory slots that appear
     * in the vanilla inventory screen and persist across sessions.
     */
    public static PlayerInventoryApi playerInventory() { return PLAYER_INVENTORY; }

    /**
     * Register an entity type to be rendered with the given renderer key on Pandorical clients.
     * Supported keys: {@code "thrown_item"}, {@code "invisible"}.
     * Must be called during server-side mod initialisation.
     *
     * @param entityType  the entity type (must already be registered in the vanilla registry)
     * @param rendererKey a renderer key string
     */
    public static void registerEntityRenderer(net.minecraft.world.entity.EntityType<?> entityType,
                                              String rendererKey) {
        EntityRendererRegistry.register(entityType, rendererKey);
    }

    // --- Internal methods (used by Pandorical core, not for consuming mods) ---

    /** @hidden */
    public static justfatlard.pandorical.content.ContentRegistry contentRegistry() { return CONTENT; }

    /** @hidden — used by InventoryMenuMixin */
    public static PlayerInventoryApiImpl playerInventoryImpl() { return PLAYER_INVENTORY; }

    /** @hidden */
    public static void registerPlayerCapabilities(UUID playerUuid, Set<String> capabilities) {
        playerCapabilities.put(playerUuid, capabilities);
    }

    /** @hidden */
    public static void markContentReady(UUID playerUuid) {
        contentReadyPlayers.add(playerUuid);
    }

    /** @hidden */
    public static void removePlayer(UUID playerUuid) {
        playerCapabilities.remove(playerUuid);
        contentReadyPlayers.remove(playerUuid);
        playerScreens.remove(playerUuid);
    }

    /** @hidden */
    public static ScreenApiImpl screensImpl() { return SCREENS; }

    private static void setPlayerScreen(UUID playerUuid, String screenType, String screenId) {
        playerScreens.put(playerUuid, new ScreenContext(screenType, screenId));
    }

    private static String getPlayerScreenType(UUID playerUuid) {
        ScreenContext ctx = playerScreens.get(playerUuid);
        return ctx != null ? ctx.screenType() : null;
    }

    private static String getPlayerScreenId(UUID playerUuid) {
        ScreenContext ctx = playerScreens.get(playerUuid);
        return ctx != null ? ctx.screenId() : null;
    }

    private static void clearPlayerScreen(UUID playerUuid) {
        playerScreens.remove(playerUuid);
    }

    // --- ScreenApi implementation ---

    public static final class ScreenApiImpl implements ScreenApi {
        private final Map<String, Map<String, BiConsumer<ServerPlayer, Map<String, String>>>> actionHandlers = new ConcurrentHashMap<>();
        private final Map<String, BiConsumer<ServerPlayer, Map<String, String>>> fallbackHandlers = new ConcurrentHashMap<>();
        private final Map<String, Consumer<ServerPlayer>> closeHandlers = new ConcurrentHashMap<>();
        private final Map<String, ScreenApi.SlotChangeHandler> slotChangeHandlers = new ConcurrentHashMap<>();
        private final Map<String, Consumer<ServerPlayer>> containerRemovedHandlers = new ConcurrentHashMap<>();

        @Override
        public void open(ServerPlayer player, justfatlard.pandorical.protocol.OpenScreenS2C screen) {
            if (!hasCapability(player, "screens")) {
                justfatlard.pandorical.Pandorical.LOGGER.debug("Cannot open screen for {} — client lacks 'screens' capability",
                    player.getName().getString());
                return;
            }
            if (screen.container().isPresent()) {
                justfatlard.pandorical.Pandorical.LOGGER.warn(
                    "Screen '{}' has a container definition but was opened with open() instead of openContainer() — " +
                    "container slots will not work. Use openContainer() for screens with inventory slots.",
                    screen.screenType());
            }
            setPlayerScreen(player.getUUID(), screen.screenType(), screen.screenId());
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, screen);
        }

        @Override
        public void openContainer(ServerPlayer player, justfatlard.pandorical.protocol.OpenScreenS2C screen,
                                  Container serverContainer, Set<Integer> readOnlySlots) {
            if (!hasCapability(player, "screens")) {
                justfatlard.pandorical.Pandorical.LOGGER.debug("Cannot open container for {} — client lacks 'screens' capability",
                    player.getName().getString());
                return;
            }
            setPlayerScreen(player.getUUID(), screen.screenType(), screen.screenId());

            // Send the screen definition FIRST — client stores it in pending map
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, screen);

            // Then open the menu — client matches it with the pending screen def
            String screenType = screen.screenType();
            int slotCount = screen.container().map(c -> c.slotCount()).orElse(0);
            player.openMenu(new PandoricalMenuProvider(screen, serverContainer, readOnlySlots,
                // slot change callback — track previous state, only report changed slots
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
                    clearPlayerScreen(player.getUUID());
                }
            ));
        }

        @Override
        public void update(ServerPlayer player, String screenId, List<justfatlard.pandorical.protocol.ComponentUpdate> updates) {
            if (!isAvailable(player)) return;
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new justfatlard.pandorical.protocol.UpdateScreenS2C(screenId, updates));
        }

        @Override
        public void close(ServerPlayer player, String screenId) {
            if (!isAvailable(player)) return;
            // Only clear tracking when this screenId is still the active one.
            // If handleResponse() opened a NEW screen before we got here, the new
            // screen's tracking must survive so its buttons can be handled.
            String currentId = getPlayerScreenId(player.getUUID());
            if (screenId.equals(currentId)) {
                clearPlayerScreen(player.getUUID());
            }
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new justfatlard.pandorical.protocol.CloseScreenS2C(screenId));
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
        public void onContainerRemoved(String screenType, Consumer<ServerPlayer> handler) {
            containerRemovedHandlers.put(screenType, handler);
        }

        public void handleAction(ServerPlayer player, justfatlard.pandorical.protocol.ScreenActionC2S action) {
            String screenType = getPlayerScreenType(player.getUUID());
            if (screenType == null) return;

            // Validate screenId matches player's actual open screen
            String expectedScreenId = getPlayerScreenId(player.getUUID());
            if (expectedScreenId != null && !expectedScreenId.equals(action.screenId())) {
                justfatlard.pandorical.Pandorical.LOGGER.warn(
                    "Player {} sent action for screen '{}' but has screen '{}' open — ignoring",
                    player.getName().getString(), action.screenId(), expectedScreenId);
                return;
            }

            // Validate C2S data payload size
            if (action.data().size() > MAX_ACTION_DATA_ENTRIES) {
                justfatlard.pandorical.Pandorical.LOGGER.warn(
                    "Player {} sent action with {} data entries (max {}) — ignoring",
                    player.getName().getString(), action.data().size(), MAX_ACTION_DATA_ENTRIES);
                return;
            }
            for (var entry : action.data().entrySet()) {
                if (entry.getKey().length() > MAX_ACTION_STRING_LENGTH || entry.getValue().length() > MAX_ACTION_STRING_LENGTH) {
                    justfatlard.pandorical.Pandorical.LOGGER.warn(
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
                Map<String, String> dataWithId = new java.util.HashMap<>(action.data());
                dataWithId.put(ScreenApi.FALLBACK_COMPONENT_ID_KEY, action.componentId());
                fallback.accept(player, dataWithId);
            } else {
                justfatlard.pandorical.Pandorical.LOGGER.debug(
                    "Unhandled screen action: screen='{}' component='{}' action='{}'",
                    screenType, action.componentId(), action.action());
            }
        }
    }

    // --- HudApi implementation ---

    public static final class HudApiImpl implements HudApi {
        @Override
        public void show(ServerPlayer player, justfatlard.pandorical.protocol.ShowHudS2C overlay) {
            if (!hasCapability(player, "hud")) {
                justfatlard.pandorical.Pandorical.LOGGER.warn(
                    "Cannot show HUD for {} — client does not support HUD rendering (not yet implemented on client)",
                    player.getName().getString());
                return;
            }
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, overlay);
        }

        @Override
        public void update(ServerPlayer player, String overlayId, List<justfatlard.pandorical.protocol.ComponentUpdate> updates) {
            if (!isAvailable(player)) return;
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new justfatlard.pandorical.protocol.UpdateHudS2C(overlayId, updates));
        }

        @Override
        public void hide(ServerPlayer player, String overlayId) {
            if (!isAvailable(player)) return;
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new justfatlard.pandorical.protocol.HideHudS2C(overlayId));
        }
    }

    // --- CameraApi implementation ---

    public static final class CameraApiImpl implements CameraApi {
        @Override
        public void setDistance(ServerPlayer player, float distance) {
            if (!hasCapability(player, "camera")) return;
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new justfatlard.pandorical.protocol.CameraHintS2C("distance",
                    Map.of("distance", String.valueOf(distance))));
        }

        @Override
        public void setPerspective(ServerPlayer player, String perspective) {
            if (!hasCapability(player, "camera")) return;
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new justfatlard.pandorical.protocol.CameraHintS2C("perspective",
                    Map.of("mode", perspective)));
        }

        @Override
        public void reset(ServerPlayer player) {
            if (!isAvailable(player)) return;
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new justfatlard.pandorical.protocol.CameraHintS2C("reset", Map.of()));
        }
    }
}
