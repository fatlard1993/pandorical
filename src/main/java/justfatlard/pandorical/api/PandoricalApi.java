package justfatlard.pandorical.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

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
    private static final SkinApiImpl SKINS = new SkinApiImpl();
    private static final RenderApiImpl RENDER = new RenderApiImpl();
    private static final AnimationApiImpl ANIMATIONS = new AnimationApiImpl();
    private static final MountApiImpl MOUNTS = new MountApiImpl();
    private static final PlayerInventoryApiImpl PLAYER_INVENTORY = new PlayerInventoryApiImpl();
    private static final BlockTintApiImpl BLOCK_TINTS = new BlockTintApiImpl();
    private static final StructureApiImpl STRUCTURES = new StructureApiImpl();
    private static final EntityOverlayApiImpl ENTITY_OVERLAYS = new EntityOverlayApiImpl();
    private static final ChestOverlayApiImpl CHEST_OVERLAYS = new ChestOverlayApiImpl();
    private static final KeybindApiImpl KEYBINDS = new KeybindApiImpl();

    /** Holds the type and ID of the screen currently open for a player. */
    private record ScreenContext(String screenType, String screenId) {}

    // --- Per-player state ---
    private static final Map<UUID, Set<String>> playerCapabilities = new ConcurrentHashMap<>();
    private static final Set<UUID> contentReadyPlayers = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> contentSyncStarted = ConcurrentHashMap.newKeySet();
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
     * Known capability strings: {@code "screens"}, {@code "hud"}, {@code "camera"},
     * {@code "structures"}, {@code "entity_overlays"}, {@code "keybinds"}.
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

    private static final java.util.List<java.util.function.Consumer<ServerPlayer>> playerReadyListeners =
        new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * Run something once per session the moment a player's Pandorical client has announced itself:
     * capabilities registered, content sync underway. This, not Fabric's JOIN event, is when
     * per-player state can be restated (chest overlays, HUD switches, inventory button faces).
     *
     * <p>JOIN fires before the Hello handshake has arrived, so every capability-gated call made
     * there is silently dropped - the call runs, sends nothing, and looks exactly like success.
     * Three mods independently hit that with chest overlays that vanished on relog before this
     * hook existed; the replay pandorical does for its own entity overlays and keybinds happens at
     * this same moment for the same reason.
     */
    public static void onPlayerReady(java.util.function.Consumer<ServerPlayer> listener) {
        playerReadyListeners.add(listener);
    }

    /** @hidden fired by the Hello handshake receiver once capabilities are registered */
    public static void firePlayerReady(ServerPlayer player) {
        for (var listener : playerReadyListeners) listener.accept(player);
    }

    /** Returns the screen API for opening, updating, and closing declarative screens. */
    public static ScreenApi screens() { return SCREENS; }
    /** Returns the HUD API for showing, updating, and hiding HUD overlays. */
    public static HudApi hud() { return HUD; }
    /** Returns the content API for registering custom blocks, items, and assets. */
    public static ContentApi content() { return CONTENT; }
    /** Returns the camera API for adjusting camera distance and perspective for a player. */
    public static CameraApi camera() { return CAMERA; }

    public static SkinApi skins() { return SKINS; }

    public static RenderApi render() { return RENDER; }

    public static AnimationApi animations() { return ANIMATIONS; }

    public static MountApi mounts() { return MOUNTS; }

    /**
     * Returns the player inventory API for registering extra inventory slots that appear
     * in the vanilla inventory screen and persist across sessions.
     */
    public static PlayerInventoryApi playerInventory() { return PLAYER_INVENTORY; }

    /** Returns the block tint API for registering biome-color and constant tint mappings. */
    public static BlockTintApi blockTints() { return BLOCK_TINTS; }

    /**
     * Returns the structure API for displaying moving, rotating clusters of blocks
     * (e.g. rideable ships) to Pandorical clients as a single batch-rendered object.
     */
    public static StructureApi structures() { return STRUCTURES; }

    private static final BannerDecalApi BANNER_DECALS = new BannerDecalApi() {
        @Override
        public void send(ServerPlayer player, java.util.Collection<Decal> decals) {
            if (decals.isEmpty()) return;
            post(player, decals.stream().map(decal -> new justfatlard.pandorical.protocol.BannerDecalsS2C.Entry(
                decal.pos().asLong(), (byte) decal.toHead().get3DDataValue(), decal.lift(), decal.fromHead(),
                decal.length(), decal.width(), decal.layers())).toList());
        }

        @Override
        public void clear(ServerPlayer player, java.util.Collection<net.minecraft.core.BlockPos> positions) {
            if (positions.isEmpty()) return;
            post(player, positions.stream().map(pos -> new justfatlard.pandorical.protocol.BannerDecalsS2C.Entry(
                pos.asLong(), (byte) 0, 0F, 0F, 0F, 0F,
                net.minecraft.world.level.block.entity.BannerPatternLayers.EMPTY)).toList());
        }

        private void post(ServerPlayer player, java.util.List<justfatlard.pandorical.protocol.BannerDecalsS2C.Entry> entries) {
            if (!net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(
                    player, justfatlard.pandorical.protocol.BannerDecalsS2C.TYPE)) return;
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new justfatlard.pandorical.protocol.BannerDecalsS2C(entries));
        }
    };

    public static BannerDecalApi bannerDecals() { return BANNER_DECALS; }

    /**
     * Returns the entity overlay API for rendering an extra texture layer over a
     * living entity's model on Pandorical clients (e.g. per-entity cosmetics).
     */
    public static EntityOverlayApi entityOverlays() { return ENTITY_OVERLAYS; }

    /**
     * Returns the chest overlay API for drawing particular chests with a
     * different texture on Pandorical clients, addressed per player.
     */
    public static ChestOverlayApi chestOverlays() { return CHEST_OVERLAYS; }

    /**
     * Returns the keybind API for receiving rebindable keybind presses from
     * Pandorical clients, with no client mod needed on the declaring mod's side.
     */
    public static KeybindApi keybinds() { return KEYBINDS; }

    private static final BlockMarkApiImpl BLOCK_MARKS = new BlockMarkApiImpl();
    public static BlockMarkApi blockMarks() { return BLOCK_MARKS; }
    public static BlockMarkApiImpl blockMarksImpl() { return BLOCK_MARKS; }

    /** Marks by level, pushed as they change and whole to whoever arrives. */
    public static final class BlockMarkApiImpl implements BlockMarkApi {
        private final java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>,
            java.util.Map<Long, java.util.Set<String>>> marks = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public void mark(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos, String mark) {
            java.util.Set<String> at = marks.computeIfAbsent(level.dimension(), k -> new java.util.concurrent.ConcurrentHashMap<>())
                .computeIfAbsent(pos.asLong(), k -> java.util.concurrent.ConcurrentHashMap.newKeySet());
            if (!at.add(mark)) return;
            tell(level, java.util.List.of(new justfatlard.pandorical.protocol.BlockMarksS2C.Entry(pos.asLong(), mark, true)));
        }

        @Override
        public void unmark(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos, String mark) {
            java.util.Map<Long, java.util.Set<String>> inLevel = marks.get(level.dimension());
            if (inLevel == null) return;
            java.util.Set<String> at = inLevel.get(pos.asLong());
            if (at == null || !at.remove(mark)) return;
            if (at.isEmpty()) inLevel.remove(pos.asLong());
            tell(level, java.util.List.of(new justfatlard.pandorical.protocol.BlockMarksS2C.Entry(pos.asLong(), mark, false)));
        }

        @Override
        public boolean isMarked(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos, String mark) {
            java.util.Map<Long, java.util.Set<String>> inLevel = marks.get(level.dimension());
            java.util.Set<String> at = inLevel == null ? null : inLevel.get(pos.asLong());
            return at != null && at.contains(mark);
        }

        /** Everything marked in the player's level, for someone who has just arrived in it. */
        public void sendAll(ServerPlayer player) {
            java.util.Map<Long, java.util.Set<String>> inLevel = marks.get(player.level().dimension());
            if (inLevel == null || inLevel.isEmpty()) return;
            java.util.List<justfatlard.pandorical.protocol.BlockMarksS2C.Entry> entries = new java.util.ArrayList<>();
            for (var e : inLevel.entrySet()) {
                for (String mark : e.getValue()) entries.add(new justfatlard.pandorical.protocol.BlockMarksS2C.Entry(e.getKey(), mark, true));
            }
            send(player, player.level(), entries);
        }

        private void tell(net.minecraft.server.level.ServerLevel level, java.util.List<justfatlard.pandorical.protocol.BlockMarksS2C.Entry> entries) {
            for (ServerPlayer player : level.players()) send(player, level, entries);
        }

        private void send(ServerPlayer player, net.minecraft.server.level.ServerLevel level, java.util.List<justfatlard.pandorical.protocol.BlockMarksS2C.Entry> entries) {
            if (!net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(
                    player, justfatlard.pandorical.protocol.BlockMarksS2C.TYPE)) return;
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new justfatlard.pandorical.protocol.BlockMarksS2C(level.dimension().identifier(), entries));
        }
    }

    private static final justfatlard.pandorical.settings.SettingsRegistry SETTINGS =
        new justfatlard.pandorical.settings.SettingsRegistry();
    /** Per-player settings, shown to the player on one screen instead of behind commands. */
    public static SettingsApi settings() { return SETTINGS; }
    public static justfatlard.pandorical.settings.SettingsRegistry settingsImpl() { return SETTINGS; }

    /**
     * Returns the screen ID of the screen currently open for this player via Pandorical,
     * or null if no Pandorical screen is open. Useful for mods that need to push updates
     * to a screen they opened earlier without tracking the ID themselves.
     */
    public static String getOpenScreenId(UUID playerUuid) {
        return getPlayerScreenId(playerUuid);
    }

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


    /** @hidden */
    public static final class ChestOverlayApiImpl implements ChestOverlayApi {
        @Override
        public void replace(ServerPlayer player, net.minecraft.resources.Identifier texture,
                java.util.Collection<net.minecraft.core.BlockPos> positions) {
            send(player, justfatlard.pandorical.protocol.ChestOverlayS2C.OP_REPLACE, texture, positions);
        }

        @Override
        public void add(ServerPlayer player, net.minecraft.resources.Identifier texture,
                java.util.Collection<net.minecraft.core.BlockPos> positions) {
            if (positions.isEmpty()) return;
            send(player, justfatlard.pandorical.protocol.ChestOverlayS2C.OP_ADD, texture, positions);
        }

        @Override
        public void remove(ServerPlayer player, java.util.Collection<net.minecraft.core.BlockPos> positions) {
            if (positions.isEmpty()) return;
            // The texture is irrelevant to a removal, and the client ignores it.
            send(player, justfatlard.pandorical.protocol.ChestOverlayS2C.OP_REMOVE,
                net.minecraft.resources.Identifier.fromNamespaceAndPath("pandorical", "none"), positions);
        }

        private static void send(ServerPlayer player, byte op, net.minecraft.resources.Identifier texture,
                java.util.Collection<net.minecraft.core.BlockPos> positions) {
            if (!hasCapability(player, "chest_overlays")) return;

            long[] packed = new long[positions.size()];
            int i = 0;
            for (net.minecraft.core.BlockPos pos : positions) packed[i++] = pos.asLong();

            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new justfatlard.pandorical.protocol.ChestOverlayS2C(op, texture.toString(), packed));
        }
    }

    // --- Internal methods (used by Pandorical core, not for consuming mods) ---

    /** @hidden */
    public static justfatlard.pandorical.content.ContentRegistry contentRegistry() { return CONTENT; }

    /** @hidden used by InventoryMenuMixin */
    public static PlayerInventoryApiImpl playerInventoryImpl() { return PLAYER_INVENTORY; }

    /** @hidden */
    public static BlockTintApiImpl blockTintsImpl() { return BLOCK_TINTS; }

    /** @hidden used by Pandorical's EntityTrackingEvents registration */
    public static StructureApiImpl structuresImpl() { return STRUCTURES; }

    /** @hidden used by Pandorical's EntityTrackingEvents/ServerEntityEvents registration */
    public static EntityOverlayApiImpl entityOverlaysImpl() { return ENTITY_OVERLAYS; }

    /** @hidden used by Pandorical's KeyPressC2S receiver and handshake push */
    public static KeybindApiImpl keybindsImpl() { return KEYBINDS; }

    /** @hidden */
    public static void registerPlayerCapabilities(UUID playerUuid, Set<String> capabilities) {
        playerCapabilities.put(playerUuid, capabilities);
    }

    /** @hidden */
    public static void markContentReady(UUID playerUuid) {
        contentReadyPlayers.add(playerUuid);
    }

    /**
     * Reserves the one content sync allowed per connection. Returns true exactly once per
     * player until they disconnect, so a client that re-sends HelloC2S (e.g. to force a resync
     * it never acknowledges) cannot repeatedly trigger the full content+asset rebuild.
     * @hidden
     */
    public static boolean beginContentSync(UUID playerUuid) {
        return contentSyncStarted.add(playerUuid);
    }

    /** @hidden */
    public static void removePlayer(UUID playerUuid) {
        playerCapabilities.remove(playerUuid);
        contentReadyPlayers.remove(playerUuid);
        contentSyncStarted.remove(playerUuid);
        playerScreens.remove(playerUuid);
        KEYBINDS.removePlayer(playerUuid);
        HUD.forgetPlayer(playerUuid);
        PLAYER_INVENTORY.forgetButtonGlyphs(playerUuid);
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
    private static void clearPlayerScreen(UUID playerUuid, String screenId) {
        playerScreens.computeIfPresent(playerUuid,
            (uuid, ctx) -> ctx.screenId().equals(screenId) ? null : ctx);
    }

    // --- ScreenApi implementation ---

    public static final class ScreenApiImpl implements ScreenApi {
        private final Map<String, Map<String, BiConsumer<ServerPlayer, Map<String, String>>>> actionHandlers = new ConcurrentHashMap<>();
        private final Map<String, BiConsumer<ServerPlayer, Map<String, String>>> fallbackHandlers = new ConcurrentHashMap<>();
        private final Map<String, Consumer<ServerPlayer>> closeHandlers = new ConcurrentHashMap<>();
        private final Map<String, ScreenApi.SlotChangeHandler> slotChangeHandlers = new ConcurrentHashMap<>();
        private final Map<String, ScreenApi.PlaceRecipeHandler> placeRecipeHandlers = new ConcurrentHashMap<>();
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
            if (player.containerMenu instanceof justfatlard.pandorical.screen.PandoricalMenu) {
                player.closeContainer();
            }

            setPlayerScreen(player.getUUID(), screen.screenType(), screen.screenId());

            // The screen definition must be sent before openMenu: the client stores it in
            // a pending map and matches the incoming menu against it.
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, screen);

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
        public void update(ServerPlayer player, String screenId, List<justfatlard.pandorical.protocol.ComponentUpdate> updates) {
            if (!isAvailable(player)) return;

            // The client matches updates on the screen ID, and drops anything else where it
            // lands. That silence is the trap: the usual mistake is addressing an update by the
            // screen TYPE, which is the constant a mod actually has on hand - ScreenBuilder mints
            // the id itself, so the two are never equal unless id() was called. The feature then
            // works perfectly on the server and never redraws, with nothing anywhere to say why.
            String open = getPlayerScreenId(player.getUUID());
            if (open != null && !open.equals(screenId)) {
                justfatlard.pandorical.Pandorical.LOGGER.warn(
                    "Screen update addressed to '{}' but {} has screen id '{}' open (type '{}') — "
                        + "the client would drop this. Pass the id from ScreenBuilder.screenId(), "
                        + "not the screen type.",
                    screenId, player.getName().getString(), open,
                    getPlayerScreenType(player.getUUID()));
                return;
            }

            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new justfatlard.pandorical.protocol.UpdateScreenS2C(screenId, updates));
        }

        @Override
        public void close(ServerPlayer player, String screenId) {
            if (!isAvailable(player)) return;
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
                if (player.containerMenu instanceof justfatlard.pandorical.screen.PandoricalMenu) {
                    player.closeContainer();
                } else {
                    clearPlayerScreen(player.getUUID());
                }
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
        public void onPlaceRecipe(String screenType, PlaceRecipeHandler handler) {
            placeRecipeHandlers.put(screenType, handler);
        }

        @Override
        public void onContainerRemoved(String screenType, Consumer<ServerPlayer> handler) {
            containerRemovedHandlers.put(screenType, handler);
        }

        public void handleAction(ServerPlayer player, justfatlard.pandorical.protocol.ScreenActionC2S action) {
            String screenType = getPlayerScreenType(player.getUUID());
            if (screenType == null) return;

            String expectedScreenId = getPlayerScreenId(player.getUUID());
            if (expectedScreenId != null && !expectedScreenId.equals(action.screenId())) {
                justfatlard.pandorical.Pandorical.LOGGER.warn(
                    "Player {} sent action for screen '{}' but has screen '{}' open — ignoring",
                    player.getName().getString(), action.screenId(), expectedScreenId);
                return;
            }

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
                var info = server.getRecipeManager().getRecipeFromDisplay(
                    new net.minecraft.world.item.crafting.display.RecipeDisplayId(displayIndex));
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

        /** player UUID to owner id to that owner's requested element ids. */
        private final Map<java.util.UUID, Map<String, java.util.Set<String>>> hiddenVanillaElements =
            new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public void hideVanillaElements(ServerPlayer player, String ownerId, java.util.Collection<String> elementIds) {
            if (!hasCapability(player, "hud_elements")) return;
            Map<String, java.util.Set<String>> byOwner = hiddenVanillaElements
                .computeIfAbsent(player.getUUID(), k -> new java.util.concurrent.ConcurrentHashMap<>());
            if (elementIds.isEmpty()) {
                byOwner.remove(ownerId);
            } else {
                byOwner.put(ownerId, java.util.Set.copyOf(elementIds));
            }
            sendVanillaElementSet(player, byOwner);
        }

        @Override
        public void restoreVanillaElements(ServerPlayer player, String ownerId) {
            Map<String, java.util.Set<String>> byOwner = hiddenVanillaElements.get(player.getUUID());
            if (byOwner == null || byOwner.remove(ownerId) == null) return;
            if (!hasCapability(player, "hud_elements")) return;
            sendVanillaElementSet(player, byOwner);
        }

        private static void sendVanillaElementSet(ServerPlayer player, Map<String, java.util.Set<String>> byOwner) {
            java.util.Set<String> union = new java.util.LinkedHashSet<>();
            for (java.util.Set<String> ids : byOwner.values()) union.addAll(ids);
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new justfatlard.pandorical.protocol.SetVanillaHudElementsS2C(java.util.List.copyOf(union)));
        }

        void forgetPlayer(java.util.UUID uuid) {
            hiddenVanillaElements.remove(uuid);
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
        public void zoom(ServerPlayer player, float factor) {
            if (!hasCapability(player, "camera")) return;
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new justfatlard.pandorical.protocol.CameraHintS2C("zoom",
                    Map.of("factor", String.valueOf(factor))));
        }

        @Override
        public void reset(ServerPlayer player) {
            if (!isAvailable(player)) return;
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new justfatlard.pandorical.protocol.CameraHintS2C("reset", Map.of()));
        }
    }

    // --- MountApi implementation ---

    public static final class MountApiImpl implements MountApi {
        private static boolean doubleRiders = false;
        private static boolean freeLook = false;

        @Override
        public void doubleRiders(boolean allow) {
            doubleRiders = allow;
            apply();
        }

        @Override
        public void freeLook(boolean enable) {
            freeLook = enable;
            apply();
        }

        /**
         * The server's own mixins read the same holder the client's do, so it is set here rather
         * than only sent. Declared at mod initialise, before any player exists; {@link #sendTo}
         * tells each client as it arrives.
         */
        private static void apply() {
            justfatlard.pandorical.MountPolicy.set(doubleRiders, freeLook);
        }

        public static void sendTo(ServerPlayer player) {
            if (!doubleRiders && !freeLook) return;
            if (!hasCapability(player, "mount_policy")) return;

            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new justfatlard.pandorical.protocol.MountPolicyS2C(doubleRiders, freeLook));
        }
    }

    // --- AnimationApi implementation ---

    public static final class AnimationApiImpl implements AnimationApi {
        /**
         * What each entity is playing, so somebody who walks into view is told about an animation
         * that started before they arrived. Keyed by network id and cleared when the entity goes.
         */
        private static final java.util.Map<Integer, justfatlard.pandorical.protocol.PlayAnimationS2C>
            PLAYING = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public void play(net.minecraft.world.entity.Entity entity, String animationId, boolean looping) {
            var payload = new justfatlard.pandorical.protocol.PlayAnimationS2C(
                entity.getId(), animationId, looping);
            PLAYING.put(entity.getId(), payload);
            broadcast(entity, payload);
        }

        @Override
        public void stop(net.minecraft.world.entity.Entity entity) {
            PLAYING.remove(entity.getId());
            broadcast(entity, new justfatlard.pandorical.protocol.PlayAnimationS2C(
                entity.getId(), "", false));
        }

        private static void broadcast(net.minecraft.world.entity.Entity entity,
                justfatlard.pandorical.protocol.PlayAnimationS2C payload) {
            if (!(entity.level() instanceof net.minecraft.server.level.ServerLevel level)) return;

            for (ServerPlayer player : level.players()) {
                if (!hasCapability(player, "animations")) continue;
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
            }
        }

        /** Catch a joining client up on everything already playing. */
        public static void sendAllTo(ServerPlayer player) {
            if (!hasCapability(player, "animations")) return;

            for (var payload : PLAYING.values()) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
            }
        }

        /** An entity that has gone is not playing anything. */
        public static void forget(int entityId) {
            PLAYING.remove(entityId);
        }
    }

    // --- RenderApi implementation ---

    public static final class RenderApiImpl implements RenderApi {
        /**
         * The policy in force, kept so a player joining later is told the same thing as everyone
         * already here. A mod declares this once at startup and never again.
         */
        private static volatile boolean cullLeaves = false;

        /**
         * Declared once, at mod initialize, before any server or player exists - so this only
         * records the answer and {@link #sendTo} does the telling as each client arrives. There is
         * deliberately no broadcast: a rendering policy is a property of the server's content, not
         * something that flips while people are looking at it.
         */
        @Override
        public void cullLeaves(boolean enforce) {
            cullLeaves = enforce;
        }

        /** Tell one arriving client what this server asks for. */
        public static void sendTo(ServerPlayer player) {
            if (!cullLeaves) return;
            if (!hasCapability(player, "render_policy")) return;
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new justfatlard.pandorical.protocol.RenderPolicyS2C(cullLeaves));
        }
    }

    // --- SkinApi implementation ---

    public static final class SkinApiImpl implements SkinApi {
        /**
         * Every override currently in force, so a player who joins later still sees them.
         *
         * <p>A skin is not an event, it is a state, and a client that missed the announcement would
         * otherwise see that person as Steve for as long as both stayed logged in. Held by subject
         * so a second call about the same player replaces the first rather than piling up.
         */
        private static final Map<java.util.UUID, justfatlard.pandorical.protocol.SkinOverrideS2C> WORN =
            new java.util.concurrent.ConcurrentHashMap<>();

        /**
         * Subjects dressed for the life of the server: a leaver's row goes unless it is one of these.
         * The two lifetimes are told apart here rather than by two maps, so the one send loop and
         * the one replay on join serve both.
         */
        private static final Set<java.util.UUID> KEPT = ConcurrentHashMap.newKeySet();

        @Override
        public void set(ServerPlayer subject, byte[] png, boolean slim) {
            if (png == null || png.length == 0) {
                clear(subject);
                return;
            }
            broadcast(subject, new justfatlard.pandorical.protocol.SkinOverrideS2C(
                subject.getUUID(), png, slim));
        }

        @Override
        public void clear(ServerPlayer subject) {
            clear(subject.level().getServer(), subject.getUUID());
        }

        @Override
        public void set(net.minecraft.server.MinecraftServer server, java.util.UUID subject,
                byte[] png, boolean slim) {
            if (png == null || png.length == 0) {
                clear(server, subject);
                return;
            }
            KEPT.add(subject);
            justfatlard.pandorical.protocol.SkinOverrideS2C worn =
                new justfatlard.pandorical.protocol.SkinOverrideS2C(subject, png, slim);
            WORN.put(subject, worn);
            send(server, worn);
        }

        @Override
        public void clear(net.minecraft.server.MinecraftServer server, java.util.UUID subject) {
            KEPT.remove(subject);
            WORN.remove(subject);
            // An empty image is how "wear your own skin again" is said; the alternative would be a
            // second packet type that means nothing else.
            send(server, new justfatlard.pandorical.protocol.SkinOverrideS2C(
                subject, new byte[0], false));
        }

        private void broadcast(ServerPlayer subject,
                justfatlard.pandorical.protocol.SkinOverrideS2C worn) {
            WORN.put(subject.getUUID(), worn);
            send(subject.level().getServer(), worn);
        }

        private static void send(net.minecraft.server.MinecraftServer server,
                justfatlard.pandorical.protocol.SkinOverrideS2C worn) {
            if (server == null) return;
            for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
                if (!hasCapability(viewer, "skins")) continue;
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(viewer, worn);
            }
        }

        /** Catch a newly arrived client up on everyone already wearing something. */
        public static void sendAllTo(ServerPlayer viewer) {
            if (WORN.isEmpty() || !hasCapability(viewer, "skins")) return;
            for (justfatlard.pandorical.protocol.SkinOverrideS2C worn : WORN.values()) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(viewer, worn);
            }
        }

        /** Drop a leaver's entry, so the map does not grow for the life of the server. */
        public static void forget(java.util.UUID subject) {
            if (!KEPT.contains(subject)) WORN.remove(subject);
        }
    }

    // --- BlockTintApi implementation ---

    public static final class BlockTintApiImpl implements BlockTintApi {
        private final java.util.List<justfatlard.pandorical.protocol.BlockTintsConfigS2C.Entry> entries =
            new java.util.ArrayList<>();

        @Override public void grass(String... blockIds)     { add("grass",     0, blockIds); }
        @Override public void stem(String... blockIds)      { add("stem",      0, blockIds); }
        @Override public void sugarCane(String... blockIds) { add("sugar_cane",0, blockIds); }
        @Override public void foliage(String... blockIds)   { add("foliage",   0, blockIds); }
        @Override public void constant(int argb, String... blockIds) { add("constant", argb, blockIds); }
        @Override public void positional(String... blockIds)          { add("positional", 0, blockIds); }
        @Override public void positional(int fallbackArgb, String... blockIds) { add("positional", fallbackArgb, blockIds); }

        @Override
        public void paint(ServerPlayer player, java.util.Map<net.minecraft.core.BlockPos, Integer> argbByPosition) {
            if (argbByPosition.isEmpty()) return;

            send(player, argbByPosition.entrySet().stream()
                .map(entry -> new justfatlard.pandorical.protocol.BlockTintPositionsS2C.Entry(
                    entry.getKey().asLong(), entry.getValue()))
                .toList());
        }

        @Override
        public void unpaint(ServerPlayer player, java.util.Collection<net.minecraft.core.BlockPos> positions) {
            if (positions.isEmpty()) return;

            // Zero is the clear: a colour with no alpha is not a colour anybody meant to paint,
            // so it can carry the other meaning without a second packet to say which.
            send(player, positions.stream()
                .map(pos -> new justfatlard.pandorical.protocol.BlockTintPositionsS2C.Entry(pos.asLong(), 0))
                .toList());
        }

        private void send(ServerPlayer player,
                java.util.List<justfatlard.pandorical.protocol.BlockTintPositionsS2C.Entry> entries) {
            // Asked rather than assumed: an older Pandorical has no receiver for this type, and
            // sending it anyway disconnects them over a colour.
            if (!net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(
                    player, justfatlard.pandorical.protocol.BlockTintPositionsS2C.TYPE)) return;

            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new justfatlard.pandorical.protocol.BlockTintPositionsS2C(entries));
        }

        private void add(String tintType, int constantColor, String[] blockIds) {
            entries.add(new justfatlard.pandorical.protocol.BlockTintsConfigS2C.Entry(
                tintType, constantColor, java.util.List.of(blockIds)));
        }

        public justfatlard.pandorical.protocol.BlockTintsConfigS2C buildPacket() {
            return new justfatlard.pandorical.protocol.BlockTintsConfigS2C(java.util.List.copyOf(entries));
        }

        public boolean hasEntries() { return !entries.isEmpty(); }
    }

    // --- StructureApi implementation ---

    /**
     * Server-side state for one structure. Broadcast-scoped (not per-player): mutated in
     * place and re-broadcast to every current tracker of {@code anchorEntity} on every call.
     */
    private static final class StructureState {
        final Entity anchorEntity;
        final Map<RelPos, BlockState> blocks;
        StructurePose pose;
        boolean visible;

        StructureState(Entity anchorEntity, Map<RelPos, BlockState> blocks, StructurePose pose, boolean visible) {
            this.anchorEntity = anchorEntity;
            this.blocks = blocks;
            this.pose = pose;
            this.visible = visible;
        }
    }

    public static final class StructureApiImpl implements StructureApi {
        private final Map<String, StructureState> structures = new ConcurrentHashMap<>();

        @Override
        public void spawn(Entity anchorEntity, String structureId, List<BlockEntry> blocks, StructurePose initialPose) {
            Map<RelPos, BlockState> blockMap = new LinkedHashMap<>();
            for (BlockEntry entry : blocks) blockMap.put(entry.pos(), entry.state());

            StructureState state = new StructureState(anchorEntity, blockMap, initialPose, true);
            structures.put(structureId, state);

            justfatlard.pandorical.protocol.SpawnStructureS2C packet = buildSpawnPacket(structureId, state);
            broadcastToTrackers(state.anchorEntity, packet);
        }

        /** Structures whose pose changed since the tracker pass last sent it. */
        private final java.util.Set<String> pendingPoses = java.util.concurrent.ConcurrentHashMap.newKeySet();

        @Override
        public void updatePose(String structureId, StructurePose pose) {
            StructureState state = structures.get(structureId);
            if (state == null) return;
            state.pose = pose;
            // Held until the game's own entity tracker runs, and sent from there: the deck and
            // everything riding it then reach the client in one pass. Sent from here, mid-tick,
            // the deck ran a full server tick ahead of the anchor, the cushions and the hull the
            // tracker sends at the start of the next tick, and the pilot stood that tick off
            // the helm and shook with it.
            pendingPoses.add(structureId);
        }

        /**
         * Send every pose changed since the last pass, for structures anchored in this level.
         *
         * <p>Called at the head of {@code ChunkMap.tick()}, which is the pass that sends every
         * tracked entity's position. Both go out together, so both land in the same client tick.
         */
        public void flushPoses(net.minecraft.server.level.ServerLevel level) {
            if (pendingPoses.isEmpty()) return;
            for (java.util.Iterator<String> it = pendingPoses.iterator(); it.hasNext();) {
                String structureId = it.next();
                StructureState state = structures.get(structureId);
                if (state == null) {
                    it.remove();
                    continue;
                }
                if (state.anchorEntity.level() != level) continue;
                it.remove();
                StructurePose pose = state.pose;
                broadcastToTrackers(state.anchorEntity, new justfatlard.pandorical.protocol.UpdateStructurePoseS2C(
                    structureId, pose.x(), pose.y(), pose.z(), pose.yaw()));
            }
        }

        @Override
        public void updateBlocks(String structureId, List<BlockEntry> added, List<RelPos> removed, Map<RelPos, BlockState> changed) {
            StructureState state = structures.get(structureId);
            if (state == null) return;

            for (BlockEntry entry : added) state.blocks.put(entry.pos(), entry.state());
            for (RelPos pos : removed) state.blocks.remove(pos);
            for (Map.Entry<RelPos, BlockState> entry : changed.entrySet()) state.blocks.put(entry.getKey(), entry.getValue());

            List<justfatlard.pandorical.protocol.StructureBlockEntry> addedWire = toWireEntries(added);
            List<justfatlard.pandorical.protocol.StructureRelPos> removedWire = removed.stream()
                .map(p -> new justfatlard.pandorical.protocol.StructureRelPos(p.x(), p.y(), p.z()))
                .toList();
            List<justfatlard.pandorical.protocol.StructureBlockEntry> changedWire = changed.entrySet().stream()
                .map(e -> new justfatlard.pandorical.protocol.StructureBlockEntry(e.getKey().x(), e.getKey().y(), e.getKey().z(), e.getValue()))
                .toList();

            broadcastToTrackers(state.anchorEntity, new justfatlard.pandorical.protocol.UpdateStructureBlocksS2C(
                structureId, addedWire, removedWire, changedWire));
        }

        @Override
        public void setVisible(String structureId, boolean visible) {
            StructureState state = structures.get(structureId);
            if (state == null) return;
            state.visible = visible;

            broadcastToTrackers(state.anchorEntity,
                new justfatlard.pandorical.protocol.SetStructureVisibleS2C(structureId, visible));
        }

        @Override
        public void despawn(String structureId) {
            StructureState state = structures.remove(structureId);
            if (state == null) return;

            broadcastToTrackers(state.anchorEntity, new justfatlard.pandorical.protocol.DespawnStructureS2C(structureId));
        }

        /** @hidden called from Pandorical's EntityTrackingEvents.START_TRACKING handler. */
        public void handleStartTracking(Entity entity, ServerPlayer player) {
            if (!hasCapability(player, "structures")) return;
            for (Map.Entry<String, StructureState> entry : structures.entrySet()) {
                if (entry.getValue().anchorEntity == entity) {
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                        buildSpawnPacket(entry.getKey(), entry.getValue()));
                }
            }
        }

        /** @hidden called from Pandorical's EntityTrackingEvents.STOP_TRACKING handler. */
        public void handleStopTracking(Entity entity, ServerPlayer player) {
            if (!isAvailable(player)) return;
            for (Map.Entry<String, StructureState> entry : structures.entrySet()) {
                if (entry.getValue().anchorEntity == entity) {
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                        new justfatlard.pandorical.protocol.DespawnStructureS2C(entry.getKey()));
                }
            }
        }

        private justfatlard.pandorical.protocol.SpawnStructureS2C buildSpawnPacket(String structureId, StructureState state) {
            return new justfatlard.pandorical.protocol.SpawnStructureS2C(
                structureId,
                toWireEntries(state.blocks),
                state.pose.x(), state.pose.y(), state.pose.z(), state.pose.yaw(),
                state.visible
            );
        }

        private List<justfatlard.pandorical.protocol.StructureBlockEntry> toWireEntries(List<BlockEntry> entries) {
            return entries.stream()
                .map(e -> new justfatlard.pandorical.protocol.StructureBlockEntry(e.pos().x(), e.pos().y(), e.pos().z(), e.state()))
                .toList();
        }

        private List<justfatlard.pandorical.protocol.StructureBlockEntry> toWireEntries(Map<RelPos, BlockState> blocks) {
            return blocks.entrySet().stream()
                .map(e -> new justfatlard.pandorical.protocol.StructureBlockEntry(e.getKey().x(), e.getKey().y(), e.getKey().z(), e.getValue()))
                .toList();
        }

        private void broadcastToTrackers(Entity anchorEntity, net.minecraft.network.protocol.common.custom.CustomPacketPayload packet) {
            for (ServerPlayer player : net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(anchorEntity)) {
                if (hasCapability(player, "structures")) {
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, packet);
                }
            }
        }
    }

    /** @hidden implementation of {@link EntityOverlayApi}; broadcast design mirrors StructureApiImpl. */
    public static final class EntityOverlayApiImpl implements EntityOverlayApi {
        private record OverlayEntry(Entity entity, net.minecraft.resources.Identifier texture) {}

        // Keyed by entity UUID; entries dropped on entity unload (see
        // handleEntityUnload). The wire protocol uses the network id, which is
        // unique per server run, so a cleared client never confuses entities.
        private final Map<UUID, OverlayEntry> overlays = new ConcurrentHashMap<>();

        @Override
        public void set(Entity entity, net.minecraft.resources.Identifier texture) {
            if (entity == null || texture == null) return;
            overlays.put(entity.getUUID(), new OverlayEntry(entity, texture));
            justfatlard.pandorical.Pandorical.LOGGER.info("Entity overlay set: {} ({}) -> {}",
                entity.getId(),
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()),
                texture);
            broadcastToTrackers(entity,
                new justfatlard.pandorical.protocol.EntityOverlayS2C(entity.getId(), texture.toString()));
        }

        @Override
        public void clear(Entity entity) {
            if (entity == null) return;
            if (overlays.remove(entity.getUUID()) == null) return;
            broadcastToTrackers(entity,
                new justfatlard.pandorical.protocol.EntityOverlayS2C(entity.getId(), ""));
        }

        /** @hidden called from Pandorical's EntityTrackingEvents.START_TRACKING handler. */
        public void handleStartTracking(Entity entity, ServerPlayer player) {
            if (!hasCapability(player, "entity_overlays")) return;
            OverlayEntry entry = overlays.get(entity.getUUID());
            if (entry != null) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                    new justfatlard.pandorical.protocol.EntityOverlayS2C(entity.getId(), entry.texture().toString()));
            }
        }

        /**
         * @hidden called after HelloC2S registers capabilities: on join, entity
         * tracking starts before the handshake completes, so overlays for
         * already-tracked entities must be replayed here.
         */
        public void handlePlayerReady(ServerPlayer player) {
            if (!hasCapability(player, "entity_overlays")) return;
            for (OverlayEntry entry : overlays.values()) {
                if (net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(entry.entity()).contains(player)) {
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                        new justfatlard.pandorical.protocol.EntityOverlayS2C(
                            entry.entity().getId(), entry.texture().toString()));
                }
            }
        }

        /** @hidden called from Pandorical's ServerEntityEvents.ENTITY_UNLOAD handler. */
        public void handleEntityUnload(Entity entity) {
            overlays.remove(entity.getUUID());
        }

        private void broadcastToTrackers(Entity entity, net.minecraft.network.protocol.common.custom.CustomPacketPayload packet) {
            for (ServerPlayer player : net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(entity)) {
                if (hasCapability(player, "entity_overlays")) {
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, packet);
                }
            }
        }
    }

    /** @hidden implementation of {@link KeybindApi}; pool model rationale in the interface javadoc. */
    public static final class KeybindApiImpl implements KeybindApi {
        /** Must match the pool the client registers at startup. */
        public static final int MAX_SLOTS = 8;
        // Key codes in the game's own InputConstants table (NOT GLFW: KEY_G is
        // 10 on this snapshot generation, and 71 is scroll lock). Literals
        // because InputConstants is a client-only class, absent on a dedicated
        // server. Slot 0 defaults to G; 0 is the unbound/unknown code.
        /** What an entry in {@link #POOL_DEFAULT_KEYS} says when the slot starts unbound. */
        private static final int UNBOUND = 0;

        // Slot 0 is the only one the client pre-binds; everything else waits to be bound by the
        // player in the controls screen. KEY_G is 10 in the game's own table, not GLFW's.
        // Two slots come pre-bound: token 10 is G, token 11 is B. A registration that names the
        // token gets the slot; see chooseSlot for why the others do not.
        private static final int[] POOL_DEFAULT_KEYS = {10, 11, UNBOUND, UNBOUND,
                                                        UNBOUND, UNBOUND, UNBOUND, UNBOUND};
        private static final int MAX_PRESSES_PER_TICK = 8;

        private record Registration(String id, String displayName, KeybindHandler handler) {}

        /** A claimed slot, as the mods menu shows it: which mod, what it is called, where it sits. */
        public record Claim(int slot, String id, String displayName) {}

        private final Map<Integer, Registration> bySlot = new ConcurrentHashMap<>();
        /** What each player's client says its pool keys are bound to, by slot; empty until it says. */
        private final Map<UUID, java.util.List<String>> bindings = new ConcurrentHashMap<>();
        private final Set<String> registeredIds = ConcurrentHashMap.newKeySet();
        // Per-player rate limit: [tick the count belongs to, dispatches that tick]
        private final Map<UUID, long[]> pressCounters = new ConcurrentHashMap<>();

        @Override
        public void register(String id, int preferredDefaultKey, String displayName, KeybindHandler handler) {
            if (id == null || displayName == null || handler == null) {
                justfatlard.pandorical.Pandorical.LOGGER.warn("Ignoring keybind registration with null id/name/handler");
                return;
            }
            if (!registeredIds.add(id)) {
                justfatlard.pandorical.Pandorical.LOGGER.warn("Keybind id '{}' already registered — ignoring", id);
                return;
            }

            int slot = chooseSlot(preferredDefaultKey);
            if (slot < 0) {
                registeredIds.remove(id);
                justfatlard.pandorical.Pandorical.LOGGER.error(
                    "Keybind pool exhausted ({} slots) — cannot register '{}'", MAX_SLOTS, id);
                return;
            }
            bySlot.put(slot, new Registration(id, displayName, handler));

            // The controls screen label for the claimed slot resolves through
            // the synced pandorical lang, overriding the client's shipped
            // "Pandorical Action N" default for this server only
            CONTENT.addLangEntries(Map.of("key.pandorical.action" + (slot + 1), displayName));

            justfatlard.pandorical.Pandorical.LOGGER.info(
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
                    justfatlard.pandorical.Pandorical.LOGGER.warn(
                        "Keybind pool has no unbound slot left: slot {} was pre-bound to key {} and"
                        + " is being given to a registration that did not ask for it", i,
                        POOL_DEFAULT_KEYS[i]);
                    return i;
                }
            }
            return -1;
        }

        /** Every slot some mod has claimed, in pool order. */
        public java.util.List<Claim> claims() {
            java.util.List<Claim> out = new java.util.ArrayList<>();
            for (int slot = 0; slot < MAX_SLOTS; slot++) {
                Registration registration = bySlot.get(slot);
                if (registration != null) out.add(new Claim(slot, registration.id(), registration.displayName()));
            }
            return out;
        }

        /** The claims whose id is namespaced to this mod. */
        public java.util.List<Claim> claimsOf(String modId) {
            java.util.List<Claim> out = new java.util.ArrayList<>();
            for (Claim claim : claims()) {
                int colon = claim.id().indexOf(':');
                if (colon > 0 && claim.id().substring(0, colon).equals(modId)) out.add(claim);
            }
            return out;
        }

        /** What this player's client has this slot bound to, or null when it has not said. */
        public String bindingOf(ServerPlayer player, int slot) {
            java.util.List<String> keys = bindings.get(player.getUUID());
            if (keys == null || slot < 0 || slot >= keys.size()) return null;
            String key = keys.get(slot);
            return key == null || key.isEmpty() ? null : key;
        }

        /** @hidden the client reporting what its pool keys are bound to. */
        public void handleBindings(ServerPlayer player, java.util.List<String> keys) {
            bindings.put(player.getUUID(), java.util.List.copyOf(keys));
            justfatlard.pandorical.settings.SettingsRegistry registry = SETTINGS;
            registry.refreshKeybinds(player);
        }

        /** Ask this player's client to bind the next key it sees to this slot. */
        public void requestRebind(ServerPlayer player, int slot) {
            if (!hasCapability(player, "keybinds")) return;
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new justfatlard.pandorical.protocol.KeybindRebindS2C(slot));
        }

        /** @hidden push claimed slots after the capability handshake completes. */
        public void handlePlayerReady(ServerPlayer player) {
            bindings.remove(player.getUUID());
            if (bySlot.isEmpty() || !hasCapability(player, "keybinds")) return;
            java.util.List<Integer> slots = new java.util.ArrayList<>(bySlot.keySet());
            java.util.Collections.sort(slots);
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new justfatlard.pandorical.protocol.KeybindDeclarationsS2C(slots));
        }

        /** @hidden validate and dispatch one press; called on the server thread. */
        public void handleKeyPress(ServerPlayer player, int slot) {
            if (!hasCapability(player, "keybinds")) return;
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
                justfatlard.pandorical.Pandorical.LOGGER.error(
                    "Keybind handler '{}' threw for player {}: {}",
                    registration.id(), player.getName().getString(), e.getMessage(), e);
            }
        }

        /** @hidden validate and dispatch one release; called on the server thread. */
        public void handleKeyRelease(ServerPlayer player, int slot) {
            if (!hasCapability(player, "keybinds")) return;
            if (slot < 0 || slot >= MAX_SLOTS) return;
            Registration registration = bySlot.get(slot);
            if (registration == null) return;
            try {
                registration.handler().onRelease(player);
            } catch (Exception e) {
                justfatlard.pandorical.Pandorical.LOGGER.error(
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
}
