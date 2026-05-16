package justfatlard.pandorical;

import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.config.PandoricalSyncTask;
import justfatlard.pandorical.protocol.*;
import justfatlard.pandorical.screen.PandoricalMenu;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Pandorical implements ModInitializer {
    public static final String MOD_ID = "pandorical";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final int PROTOCOL_VERSION = 1;

    public static final List<String> SERVER_CAPABILITIES = List.of("screens", "content", "camera", "hud");

    /**
     * Tracks player UUIDs (from GameProfile) that completed config-phase content sync.
     * Entries are consumed (removed) when the player transitions to play phase.
     */
    private static final Set<java.util.UUID> configPhaseSyncedPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @SuppressWarnings("unchecked")
    public static final MenuType<PandoricalMenu> MENU_TYPE = (MenuType<PandoricalMenu>) Registry.register(
        BuiltInRegistries.MENU,
        Identifier.fromNamespaceAndPath(MOD_ID, "container"),
        new MenuType<>(PandoricalMenu::new, FeatureFlags.VANILLA_SET)
    );


    @Override
    public void onInitialize() {
        // Auto-detect and register all non-system mod namespaces as server-only.
        // Only on dedicated server — on client this would incorrectly filter everything.
        if (net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType() == net.fabricmc.api.EnvType.SERVER) {
            autoRegisterServerOnlyNamespaces();
            // Auto-scan assets for ALL server-only mods
            PandoricalApi.contentRegistry().autoScanAllModAssets();
        }

        registerPayloads();
        registerConfigPhase();
        registerServerHandlers();

        LOGGER.info("Pandorical initialized — protocol v{}, server-only namespaces: {}",
            PROTOCOL_VERSION,
            justfatlard.pandorical.content.ContentRegistry.getServerOnlyNamespaces());
    }


    /**
     * Mod ID prefixes that are part of Fabric's infrastructure and should NOT
     * be marked as server-only (they're handled by Fabric itself).
     */
    private static final Set<String> SYSTEM_MOD_PREFIXES = Set.of(
        "java", "minecraft", "fabricloader", "fabric-api", "fabric-",
        "mixinextras"
    );

    /**
     * Scans all loaded mods and registers their mod IDs as server-only namespaces
     * for registry sync bypass. Skips system mods (Fabric infrastructure, Java, Minecraft).
     */
    private void autoRegisterServerOnlyNamespaces() {
        var contentApi = PandoricalApi.content();
        var loader = net.fabricmc.loader.api.FabricLoader.getInstance();

        for (var mod : loader.getAllMods()) {
            String modId = mod.getMetadata().getId();
            if (isSystemMod(modId)) continue;
            contentApi.registerServerOnlyNamespace(modId);
        }
    }

    private boolean isSystemMod(String modId) {
        for (String prefix : SYSTEM_MOD_PREFIXES) {
            if (modId.equals(prefix) || modId.startsWith(prefix + "-")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Factory method called by PandoricalMenuProvider to create a server-side menu.
     */
    public static AbstractContainerMenu createMenu(int syncId, Inventory playerInventory,
                                                    Container serverContainer, Set<Integer> readOnlySlots,
                                                    OpenScreenS2C screenDef,
                                                    Runnable slotChangeCallback, Runnable removedCallback) {
        PandoricalMenu menu = new PandoricalMenu(MENU_TYPE, syncId, playerInventory,
            serverContainer, readOnlySlots, screenDef);
        menu.setSlotChangeCallback(slotChangeCallback);
        menu.setRemovedCallback(removedCallback);
        return menu;
    }

    private void registerPayloads() {
        // --- Configuration phase ---
        // S2C config
        PayloadTypeRegistry.clientboundConfiguration().register(SyncContentConfigS2C.TYPE, SyncContentConfigS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundConfiguration().register(SyncAssetsConfigS2C.TYPE, SyncAssetsConfigS2C.STREAM_CODEC);
        // C2S config
        PayloadTypeRegistry.serverboundConfiguration().register(ContentReadyConfigC2S.TYPE, ContentReadyConfigC2S.STREAM_CODEC);

        // --- Play phase ---
        // S2C play
        PayloadTypeRegistry.clientboundPlay().register(HelloS2C.TYPE, HelloS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenScreenS2C.TYPE, OpenScreenS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(UpdateScreenS2C.TYPE, UpdateScreenS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CloseScreenS2C.TYPE, CloseScreenS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ShowHudS2C.TYPE, ShowHudS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(UpdateHudS2C.TYPE, UpdateHudS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(HideHudS2C.TYPE, HideHudS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SyncContentS2C.TYPE, SyncContentS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SyncAssetsS2C.TYPE, SyncAssetsS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CameraHintS2C.TYPE, CameraHintS2C.STREAM_CODEC);

        // C2S play
        PayloadTypeRegistry.serverboundPlay().register(HelloC2S.TYPE, HelloC2S.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ScreenActionC2S.TYPE, ScreenActionC2S.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ContentReadyC2S.TYPE, ContentReadyC2S.STREAM_CODEC);
    }

    /**
     * Register configuration-phase task and handlers.
     * Content sync happens here BEFORE Fabric's SynchronizeRegistriesTask.
     */
    private void registerConfigPhase() {
        // Server: handle client acknowledgment during config phase
        ServerConfigurationNetworking.registerGlobalReceiver(ContentReadyConfigC2S.TYPE, (payload, context) -> {
            var handler = context.packetListener();
            var profile = handler.getOwner();
            if (profile != null) {
                configPhaseSyncedPlayers.add(profile.id());
                LOGGER.info("Client {} completed config-phase content sync", profile.name());
            } else {
                LOGGER.info("Client completed config-phase content sync (unknown profile)");
            }
            handler.completeTask(PandoricalSyncTask.TYPE);
        });

        // Server: add our sync task BEFORE Fabric's registry sync
        ServerConfigurationConnectionEvents.BEFORE_CONFIGURE.register((handler, server) -> {
            if (ServerConfigurationNetworking.canSend(handler, SyncContentConfigS2C.TYPE)) {
                var contentRegistry = PandoricalApi.contentRegistry();
                if (contentRegistry.hasContent()) {
                    try {
                        var blocks = contentRegistry.buildBlockEntries();
                        var items = contentRegistry.buildItemEntries();
                        var assetChunks = contentRegistry.buildConfigAssetChunks();
                        var entityTypes = contentRegistry.buildEntityTypeEntries();
                        var blockEntityTypes = contentRegistry.buildBlockEntityTypeEntries();
                        var villagerProfessions = contentRegistry.buildVillagerProfessionEntries();
                        var poiTypes = contentRegistry.buildPoiTypeEntries();
                        var menuTypes = contentRegistry.buildMenuTypeEntries();
                        var recipeBookCategories = contentRegistry.buildRecipeBookCategoryEntries();
                        // Use reflection to add task at the FRONT of the queue
                        // so it runs before Fabric's SynchronizeRegistriesTask
                        var task = new PandoricalSyncTask(blocks, items, assetChunks,
                            entityTypes, blockEntityTypes, villagerProfessions,
                            poiTypes, menuTypes, recipeBookCategories);
                        try {
                            var field = net.minecraft.server.network.ServerConfigurationPacketListenerImpl.class
                                .getDeclaredField("configurationTasks");
                            field.setAccessible(true);
                            @SuppressWarnings("unchecked")
                            var queue = (java.util.Queue<net.minecraft.server.network.ConfigurationTask>) field.get(handler);
                            // Insert at front by creating a new deque
                            var newQueue = new java.util.ArrayDeque<net.minecraft.server.network.ConfigurationTask>();
                            newQueue.add(task);
                            newQueue.addAll(queue);
                            queue.clear();
                            queue.addAll(newQueue);
                        } catch (Exception e) {
                            LOGGER.warn("Failed to insert task at front of queue, using addTask", e);
                            handler.addTask(task);
                        }
                        LOGGER.info("Added PandoricalSyncTask for config phase ({} blocks, {} items)",
                            blocks.size(), items.size());
                    } catch (IOException e) {
                        LOGGER.error("Failed to build config-phase asset chunks", e);
                    }
                }
            } else {
                LOGGER.debug("Client does not support config-phase content sync — will use play-phase fallback");
            }
        });
    }

    private void registerServerHandlers() {
        ServerPlayNetworking.registerGlobalReceiver(HelloC2S.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                var player = context.player();

                // Validate protocol version
                if (payload.protocolVersion() != PROTOCOL_VERSION) {
                    LOGGER.warn("Player {} has Pandorical protocol v{} (server is v{}) — features may not work correctly",
                        player.getName().getString(), payload.protocolVersion(), PROTOCOL_VERSION);
                }

                PandoricalApi.registerPlayerCapabilities(
                    player.getUUID(),
                    new HashSet<>(payload.capabilities())
                );
                LOGGER.debug("Player {} connected with Pandorical v{}, capabilities: {}",
                    player.getName().getString(),
                    payload.protocolVersion(),
                    payload.capabilities());

                // Sync content if available and not already done in config phase.
                var contentRegistry = PandoricalApi.contentRegistry();
                if (contentRegistry.hasContent()
                        && PandoricalApi.hasCapability(player, "content")
                        && !PandoricalApi.isContentReady(player)) {
                    contentRegistry.syncContentTo(player);
                }

                // Re-send inventory now that the player is fully registered.
                player.inventoryMenu.sendAllDataToRemote();
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ScreenActionC2S.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                PandoricalApi.screensImpl().handleAction(context.player(), payload);
            });
        });

        // Content ready acknowledgment (play-phase fallback for non-registry content)
        ServerPlayNetworking.registerGlobalReceiver(ContentReadyC2S.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                var player = context.player();
                if (PandoricalApi.isContentReady(player)) {
                    LOGGER.debug("Player {} sent play-phase ContentReady but was already ready from config phase",
                        player.getName().getString());
                    return;
                }
                PandoricalApi.markContentReady(player.getUUID());
                LOGGER.info("Player {} content ready (play-phase fallback)", player.getName().getString());
            });
        });

        // On join: send hello. If client completed config-phase sync, mark content ready immediately.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            sender.sendPacket(new HelloS2C(PROTOCOL_VERSION, SERVER_CAPABILITIES));

            // Track config-phase players for later — don't pre-register capabilities yet
            // because the client can't deserialize full component data (armor materials, etc.)
            // until after the handshake. Inventory will be re-sent in HelloC2S handler.
            var player = handler.getPlayer();
            if (configPhaseSyncedPlayers.remove(player.getGameProfile().id())) {
                PandoricalApi.markContentReady(player.getUUID());
                LOGGER.debug("Player {} completed config-phase sync — awaiting HelloC2S for full inventory", player.getName().getString());
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PandoricalApi.removePlayer(handler.getPlayer().getUUID());
        });
    }
}
