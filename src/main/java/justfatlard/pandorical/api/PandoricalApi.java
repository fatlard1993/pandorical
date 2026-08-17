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

    /** Returns the block tint API for registering biome-color and constant tint mappings. */
    public static BlockTintApi blockTints() { return BLOCK_TINTS; }

    /**
     * Returns the structure API for displaying moving, rotating clusters of blocks
     * (e.g. rideable ships) to Pandorical clients as a single batch-rendered object.
     */
    public static StructureApi structures() { return STRUCTURES; }

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

            // The screen definition must be sent before openMenu: the client stores it in
            // a pending map and matches the incoming menu against it.
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, screen);

            String screenType = screen.screenType();
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
        public void reset(ServerPlayer player) {
            if (!isAvailable(player)) return;
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new justfatlard.pandorical.protocol.CameraHintS2C("reset", Map.of()));
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

        @Override
        public void updatePose(String structureId, StructurePose pose) {
            StructureState state = structures.get(structureId);
            if (state == null) return;
            state.pose = pose;

            broadcastToTrackers(state.anchorEntity, new justfatlard.pandorical.protocol.UpdateStructurePoseS2C(
                structureId, pose.x(), pose.y(), pose.z(), pose.yaw()));
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
        private static final int[] POOL_DEFAULT_KEYS = {10, 0, 0, 0, 0, 0, 0, 0};
        private static final int MAX_PRESSES_PER_TICK = 8;

        private record Registration(String id, String displayName, KeybindHandler handler) {}

        private final Map<Integer, Registration> bySlot = new ConcurrentHashMap<>();
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
                id, slot, displayName, POOL_DEFAULT_KEYS[slot] == -1 ? "unbound" : POOL_DEFAULT_KEYS[slot]);
        }

        private int chooseSlot(int preferredDefaultKey) {
            for (int i = 0; i < MAX_SLOTS; i++) {
                if (!bySlot.containsKey(i) && POOL_DEFAULT_KEYS[i] == preferredDefaultKey) return i;
            }
            for (int i = 0; i < MAX_SLOTS; i++) {
                if (!bySlot.containsKey(i)) return i;
            }
            return -1;
        }

        /** @hidden push claimed slots after the capability handshake completes. */
        public void handlePlayerReady(ServerPlayer player) {
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

        /** @hidden */
        public void removePlayer(UUID playerUuid) {
            pressCounters.remove(playerUuid);
        }
    }
}
