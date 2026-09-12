package justfatlard.pandorical;

import justfatlard.pandorical.api.Capabilities;
import justfatlard.pandorical.api.EntityRendererRegistry;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.PlayerInventoryApi;
import justfatlard.pandorical.api.PlayerInventoryApiImpl;
import justfatlard.pandorical.api.SettingsApi;
import justfatlard.pandorical.drops.DropsPolicy;
import justfatlard.pandorical.login.ConfigPatience;
import justfatlard.pandorical.login.PandoricalSyncTask;
import justfatlard.pandorical.protocol.*;
import justfatlard.pandorical.push.DeclaredMountPolicy;
import justfatlard.pandorical.push.DeclaredRenderPolicy;
import justfatlard.pandorical.push.PlayingAnimations;
import justfatlard.pandorical.push.SkinOverrides;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import justfatlard.pandorical.content.ContentRegistry;
import justfatlard.pandorical.login.Keepsakes;
import justfatlard.pandorical.mixin.ServerCommonConnectionAccessor;
import justfatlard.pandorical.picture.PictureRegistry;
import justfatlard.pandorical.portal.PortalPairing;
import justfatlard.pandorical.screen.Viewport;
import justfatlard.pandorical.settings.ClientMods;
import justfatlard.pandorical.settings.ModCommands;
import justfatlard.pandorical.settings.SettingsCommand;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

public class Pandorical implements ModInitializer {
    public static final String MOD_ID = "pandorical";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    /** Bumped by any change to the shape of a payload; an older reader cannot skip a new field. */
    public static final int PROTOCOL_VERSION = 15;

    /**
     * The oldest client protocol this server accepts. Raise it when an existing payload changes
     * shape; a new payload type an older client never asks for does not need it.
     */
    public static final int MINIMUM_PROTOCOL = 11;

    public static String modVersion() {
        return FabricLoader.getInstance()
            .getModContainer(MOD_ID)
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");
    }

    public static final List<String> SERVER_CAPABILITIES = Capabilities.SERVER;

    /**
     * Players whose config-phase sync completed; JOIN consumes the entry. A stale id from a
     * connection that never joined is harmless. Must not double as the repeat-ack guard, which
     * is per connection: keyed on the player, one dead connection blocks every later join.
     */
    private static final Set<UUID> configPhaseSyncedPlayers = ConcurrentHashMap.newKeySet();

    private static final Set<Object> ackedConfigConnections = ConcurrentHashMap.newKeySet();

    @SuppressWarnings("unchecked")
    public static final MenuType<PandoricalMenu> MENU_TYPE = (MenuType<PandoricalMenu>) Registry.register(
        BuiltInRegistries.MENU,
        Identifier.fromNamespaceAndPath(MOD_ID, "container"),
        new MenuType<>(PandoricalMenu::new, FeatureFlags.VANILLA_SET)
    );


    @Override
    public void onInitialize() {
        DiagnosticMixinPlugin.reportUnmatched();
        // First, so the settings exist however far the rest of init gets.
        SettingsApi.Group settings = PandoricalApi.settings().serverGroup(MOD_ID, "Pandorical");
        settings.toggle("pairNetherPortals", "Nether portals go back the way they came", false)
            .describe("Each portal remembers the one its first traveller came out of, both ways round")
            .backedBy(player -> PortalPairing.enabled(player.level().getServer()),
                (player, on) -> PortalPairing.choose(player.level().getServer(), on));
        settings.toggle("clumpExperience", "XP orbs clump and are taken at once", false)
            .describe("Nearby orbs of any value merge into one, and a touch takes all of it")
            .shownWhen(player -> !DropsPolicy.clumpsInstalled())
            .backedBy(player -> DropsPolicy.clumping(player.level().getServer()),
                (player, on) -> DropsPolicy.chooseClumping(player.level().getServer(), on));
        settings.number("itemMergeRadius", "Dropped stacks merge this far apart, in tenths of a block",
                DropsPolicy.VANILLA_MERGE_TENTHS, DropsPolicy.MOST_MERGE_TENTHS, 5, DropsPolicy.VANILLA_MERGE_TENTHS)
            .describe("5 is vanilla's. Stacks never merge through a block, or past a full stack")
            .shownWhen(player -> !DropsPolicy.getItTogetherInstalled())
            .backedBy(player -> DropsPolicy.mergeRadius(player.level().getServer()),
                (player, tenths) -> DropsPolicy.chooseMergeRadius(player.level().getServer(), tenths));

        // Dedicated server only: on a client this would filter every namespace.
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
            autoRegisterServerOnlyNamespaces();
            PandoricalApi.contentRegistry().autoScanAllModAssets();
        }

        registerPayloads();
        registerConfigPhase();
        registerServerHandlers();
        registerStructureTracking();

        LOGGER.info("Pandorical initialized — protocol v{}, server-only namespaces: {}",
            PROTOCOL_VERSION,
            ContentRegistry.getServerOnlyNamespaces());
    }


    private static final Set<String> SYSTEM_MOD_PREFIXES = Set.of(
        "java", "minecraft", "fabricloader", "fabric-api", "fabric-",
        "mixinextras"
    );

    private void autoRegisterServerOnlyNamespaces() {
        var contentApi = PandoricalApi.content();
        var loader = FabricLoader.getInstance();

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
        PayloadTypeRegistry.clientboundConfiguration().register(SyncContentConfigS2C.TYPE, SyncContentConfigS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundConfiguration().register(SyncAssetsConfigS2C.TYPE, SyncAssetsConfigS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundConfiguration().register(PlayerInventoryRegistrationsS2C.TYPE, PlayerInventoryRegistrationsS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundConfiguration().register(
            RequirementS2C.TYPE,
            RequirementS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundConfiguration().register(
            InventoryButtonsS2C.TYPE,
            InventoryButtonsS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundConfiguration().register(BlockTintsConfigS2C.TYPE, BlockTintsConfigS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundConfiguration().register(
            KeepsakesAskConfigS2C.TYPE,
            KeepsakesAskConfigS2C.STREAM_CODEC);
        PayloadTypeRegistry.serverboundConfiguration().register(ContentReadyConfigC2S.TYPE, ContentReadyConfigC2S.STREAM_CODEC);
        PayloadTypeRegistry.serverboundConfiguration().register(
            KeepsakesConfigC2S.TYPE,
            KeepsakesConfigC2S.STREAM_CODEC);

        PayloadTypeRegistry.clientboundPlay().register(HelloS2C.TYPE, HelloS2C.STREAM_CODEC);
        PictureRegistry.register();
        PayloadTypeRegistry.clientboundPlay().register(
            KeepsakeStoreS2C.TYPE,
            KeepsakeStoreS2C.STREAM_CODEC);
        // Also in play, so a switch button can change face while the inventory is open.
        PayloadTypeRegistry.clientboundPlay().register(
            InventoryButtonsS2C.TYPE,
            InventoryButtonsS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
            BlockTintPositionsS2C.TYPE,
            BlockTintPositionsS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
            BlockMarksS2C.TYPE,
            BlockMarksS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
            BannerDecalsS2C.TYPE,
            BannerDecalsS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
            ClientSettingS2C.TYPE,
            ClientSettingS2C.STREAM_CODEC);
        // Large: a screen can carry a mod's readme.
        PayloadTypeRegistry.clientboundPlay().registerLarge(OpenScreenS2C.TYPE, OpenScreenS2C.STREAM_CODEC, 1048576);
        PayloadTypeRegistry.clientboundPlay().register(UpdateScreenS2C.TYPE, UpdateScreenS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CloseScreenS2C.TYPE, CloseScreenS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ShowHudS2C.TYPE, ShowHudS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(UpdateHudS2C.TYPE, UpdateHudS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(HideHudS2C.TYPE, HideHudS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SetVanillaHudElementsS2C.TYPE, SetVanillaHudElementsS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SyncContentS2C.TYPE, SyncContentS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SyncAssetsS2C.TYPE, SyncAssetsS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CameraHintS2C.TYPE, CameraHintS2C.STREAM_CODEC);
        // Otherwise the playing table only grows, and all of it is re-sent to every joiner.
        ServerEntityEvents.ENTITY_UNLOAD.register(
            (entity, level) -> PlayingAnimations.forget(entity.getId()));

        PayloadTypeRegistry.clientboundPlay().register(
            MountPolicyS2C.TYPE,
            MountPolicyS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
            PlayAnimationS2C.TYPE,
            PlayAnimationS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
            RenderPolicyS2C.TYPE,
            RenderPolicyS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().registerLarge(
            SkinOverrideS2C.TYPE,
            SkinOverrideS2C.STREAM_CODEC, 1048576);
        PayloadTypeRegistry.clientboundPlay().register(EntityRenderersS2C.TYPE, EntityRenderersS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SpawnStructureS2C.TYPE, SpawnStructureS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(UpdateStructurePoseS2C.TYPE, UpdateStructurePoseS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(UpdateStructureBlocksS2C.TYPE, UpdateStructureBlocksS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SetStructureVisibleS2C.TYPE, SetStructureVisibleS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(DespawnStructureS2C.TYPE, DespawnStructureS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(EntityOverlayS2C.TYPE, EntityOverlayS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
            ChestOverlayS2C.TYPE,
            ChestOverlayS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(KeybindDeclarationsS2C.TYPE, KeybindDeclarationsS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
            KeybindDefaultsS2C.TYPE, KeybindDefaultsS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
            KeybindRebindS2C.TYPE,
            KeybindRebindS2C.STREAM_CODEC);

        PayloadTypeRegistry.serverboundPlay().register(HelloC2S.TYPE, HelloC2S.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ScreenActionC2S.TYPE, ScreenActionC2S.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ContentReadyC2S.TYPE, ContentReadyC2S.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(KeyPressC2S.TYPE, KeyPressC2S.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
            KeybindBindingsC2S.TYPE,
            KeybindBindingsC2S.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
            KeyReleaseC2S.TYPE, KeyReleaseC2S.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
            InventoryButtonC2S.TYPE,
            InventoryButtonC2S.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
            OpenSettingsC2S.TYPE,
            OpenSettingsC2S.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
            ClientSettingsC2S.TYPE,
            ClientSettingsC2S.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
            ViewportC2S.TYPE,
            ViewportC2S.STREAM_CODEC);
    }

    private void registerConfigPhase() {
        ServerConfigurationNetworking.registerGlobalReceiver(
            KeepsakesConfigC2S.TYPE, (payload, context) -> {
                var handler = context.packetListener();
                context.server().execute(() ->
                    Keepsakes.INSTANCE.answered(handler, payload));
            });
        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
            Keepsakes.INSTANCE.begin(handler);
            if (Keepsakes.INSTANCE.askable(handler)) {
                handler.addTask(new Keepsakes.Task());
            }
        });

        ServerConfigurationNetworking.registerGlobalReceiver(ContentReadyConfigC2S.TYPE, (payload, context) -> {
            var handler = context.packetListener();

            // completeTask starts vanilla's SynchronizeRegistriesTask, which reads server
            // registries, so this runs on the server thread.
            context.server().execute(() -> {
                var profile = handler.getOwner();

                // Only the first ack on this connection may complete the task; a second throws,
                // uncaught, on the server thread. Keyed on the connection, not the player: a
                // reconnect is a new connection with its own task to complete.
                if (!ackedConfigConnections.add(handler)) {
                    LOGGER.debug("Ignoring repeat config-phase ack from {}",
                        profile != null ? profile.name() : "(unknown profile)");
                    return;
                }
                if (profile != null) configPhaseSyncedPlayers.add(profile.id());
                ConfigPatience.end(handler, ((ServerCommonConnectionAccessor) handler).pandorical$connection());
                LOGGER.info("Client {} completed config-phase content sync",
                    profile != null ? profile.name() : "(unknown profile)");

                // Not before the ack: a tint naming a block the client has not registered yet
                // is dropped.
                sendConfigPhaseBlockTints(handler);

                try {
                    handler.completeTask(PandoricalSyncTask.TYPE);
                } catch (IllegalStateException e) {
                    LOGGER.warn("Could not complete config-phase task: {}", e.getMessage());
                }
            });
        });

        // Leave configPhaseSyncedPlayers alone: this may also fire on the hand-off into play,
        // before JOIN reads it.
        ServerConfigurationConnectionEvents.DISCONNECT.register((handler, server) -> {
            ackedConfigConnections.remove(handler);
            ConfigPatience.forget(handler);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> ConfigPatience.expire());

        ServerConfigurationConnectionEvents.BEFORE_CONFIGURE.register((handler, server) -> {
            if (!agreeOnVersion(handler)) return;

            if (ServerConfigurationNetworking.canSend(handler, SyncContentConfigS2C.TYPE)) {
                // Before play: the client builds InventoryMenu on entry, and the slot counts
                // must already agree.
                sendConfigPhaseInventoryRegistrations(handler);

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
                        // Content must reach the client before the registry sync that assigns
                        // its ids. The queue is still empty at BEFORE_CONFIGURE; inserting at
                        // the front keeps the order if vanilla ever fills it earlier.
                        var task = new PandoricalSyncTask(blocks, items, assetChunks,
                            entityTypes, blockEntityTypes, villagerProfessions,
                            poiTypes, menuTypes, recipeBookCategories, contentRegistry.railsSolid());
                        try {
                            var field = ServerConfigurationPacketListenerImpl.class
                                .getDeclaredField("configurationTasks");
                            field.setAccessible(true);
                            @SuppressWarnings("unchecked")
                            var queue = (Queue<ConfigurationTask>) field.get(handler);
                            var newQueue = new ArrayDeque<ConfigurationTask>();
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
                        ConfigPatience.begin(handler,
                            ((ServerCommonConnectionAccessor) handler).pandorical$connection());
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

                var contentRegistry = PandoricalApi.contentRegistry();
                if (contentRegistry.hasContent()
                        && PandoricalApi.hasCapability(player, Capabilities.CONTENT)
                        && !PandoricalApi.isContentReady(player)
                        && PandoricalApi.beginContentSync(player.getUUID())) {
                    contentRegistry.syncContentTo(player);
                }

                sendEntityRenderers(player);
                // Tracking starts before this handshake completes, so state sent to tracked
                // entities so far has to be replayed.
                PandoricalApi.entityOverlaysImpl().handlePlayerReady(player);
                PandoricalApi.keybindsImpl().handlePlayerReady(player);
                SkinOverrides.sendAllTo(player);
                DeclaredRenderPolicy.sendTo(player);
                PlayingAnimations.sendAllTo(player);
                DeclaredMountPolicy.sendTo(player);

                PandoricalApi.firePlayerReady(player);
            });
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ModCommands.forget();
            PandoricalApi.blockMarksImpl().clear();
            PandoricalApi.structuresImpl().clear();
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register(
            (server, resources, success) -> ModCommands.forget());
        ServerPlayNetworking.registerGlobalReceiver(
            KeybindBindingsC2S.TYPE, (payload, context) -> {
                context.player().level().getServer().execute(() ->
                    PandoricalApi.keybindsImpl().handleBindings(context.player(), payload.keys()));
            });
        ServerPlayNetworking.registerGlobalReceiver(KeyPressC2S.TYPE, (payload, context) -> {
            context.server().execute(() ->
                PandoricalApi.keybindsImpl().handleKeyPress(context.player(), payload.slot()));
        });
        ServerPlayNetworking.registerGlobalReceiver(
            KeyReleaseC2S.TYPE, (payload, context) -> {
                context.server().execute(() ->
                    PandoricalApi.keybindsImpl().handleKeyRelease(context.player(), payload.slot()));
            });

        ServerPlayNetworking.registerGlobalReceiver(
            InventoryButtonC2S.TYPE, (payload, context) -> {
                context.server().execute(() -> PandoricalApi.playerInventoryImpl()
                    .handleButton(context.player(), payload.namespace(), payload.id()));
            });

        ServerPlayNetworking.registerGlobalReceiver(
            OpenSettingsC2S.TYPE, (payload, context) -> {
                context.server().execute(() -> PandoricalApi.settings().open(context.player()));
            });
        ServerPlayNetworking.registerGlobalReceiver(
            ClientSettingsC2S.TYPE, (payload, context) -> {
                context.server().execute(() ->
                    ClientMods.declare(context.player(), payload));
            });
        ServerPlayNetworking.registerGlobalReceiver(
            ViewportC2S.TYPE, (payload, context) -> {
                context.server().execute(() ->
                    Viewport.declare(context.player(), payload));
            });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ClientMods.forget(handler.player);
            Viewport.forget(handler.player);
        });

        PandoricalApi.settingsImpl().init();
        PandoricalApi.onPlayerReady(player -> PandoricalApi.blockMarksImpl().sendAll(player));
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register(
            (player, origin, destination) -> PandoricalApi.blockMarksImpl().sendAll(player));
        CommandRegistrationCallback.EVENT.register(
            (dispatcher, registry, environment) ->
                SettingsCommand.register(dispatcher, PandoricalApi.settingsImpl()));

        ServerPlayNetworking.registerGlobalReceiver(ScreenActionC2S.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                PandoricalApi.screensImpl().handleAction(context.player(), payload);
            });
        });

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

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            sender.sendPacket(new HelloS2C(PROTOCOL_VERSION, SERVER_CAPABILITIES));

            // Capabilities wait for HelloC2S: until the handshake the client cannot decode full
            // component data.
            var player = handler.getPlayer();
            if (configPhaseSyncedPlayers.remove(player.getGameProfile().id())) {
                PandoricalApi.markContentReady(player.getUUID());
                LOGGER.debug("Player {} completed config-phase sync — awaiting HelloC2S for full inventory", player.getName().getString());
            }

            // The menu's copy of the extra slots predates the player-data load; refresh it.
            PandoricalApi.playerInventoryImpl().syncMenuFromAttachment(player);
        });

        // Likewise on respawn: the new player's menu is built before restoreFrom copies the
        // attachment over.
        ServerPlayerEvents.AFTER_RESPAWN.register(
            (oldPlayer, newPlayer, alive) ->
                PandoricalApi.playerInventoryImpl().syncMenuFromAttachment(newPlayer));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PandoricalApi.removePlayer(handler.getPlayer().getUUID());
            SkinOverrides.forget(handler.getPlayer().getUUID());
            Keepsakes.INSTANCE.forget(handler.getPlayer().getUUID());
            PandoricalApi.settingsImpl().forget(handler.getPlayer().getUUID());
        });
    }

    /**
     * Turns away, with the version to install, a client whose Pandorical is too old to read what
     * is about to be sent. A client without Pandorical passes untouched.
     *
     * @return false if the client was turned away and nothing more should be sent to it
     */
    private static boolean agreeOnVersion(
            ServerConfigurationPacketListenerImpl handler) {
        if (!ServerConfigurationNetworking.canSend(handler, SyncContentConfigS2C.TYPE)) return true;

        if (ServerConfigurationNetworking.canSend(handler,
                RequirementS2C.TYPE)) {
            ServerConfigurationNetworking.send(handler,
                new RequirementS2C(
                    PROTOCOL_VERSION, MINIMUM_PROTOCOL, modVersion()));
            return true;
        }

        // A client that cannot receive RequirementS2C predates the current content format.
        String needed = modVersion();
        LOGGER.warn("Refused a client running a Pandorical older than {}: it cannot read this"
            + " server's content format", needed);
        handler.disconnect(Component.literal(
            "Your Pandorical is out of date.\n\n"
            + "This server needs Pandorical " + needed + " or newer.\n"
            + "Replace the pandorical jar in your mods folder and reconnect."));
        return false;
    }

    private static void sendConfigPhaseInventoryRegistrations(
            ServerConfigurationPacketListenerImpl handler) {
        List<PlayerInventoryApi.SlotRegistration> regs = PandoricalApi.playerInventoryImpl().getRegistrations();
        if (regs.isEmpty()) return;

        List<PlayerInventoryRegistrationsS2C.SlotGroup> groups = new ArrayList<>();
        for (PlayerInventoryApi.SlotRegistration reg : regs) {
            List<PlayerInventoryRegistrationsS2C.SlotPosition> positions = new ArrayList<>();
            for (PlayerInventoryApi.SlotEntry entry : reg.slots()) {
                positions.add(new PlayerInventoryRegistrationsS2C.SlotPosition(
                    entry.slotIndex(), entry.screenX(), entry.screenY(), entry.backgroundSprite()));
            }
            groups.add(new PlayerInventoryRegistrationsS2C.SlotGroup(
                reg.namespace().toString(), positions));
        }

        ServerConfigurationNetworking.send(handler, new PlayerInventoryRegistrationsS2C(groups));

        var buttons = PandoricalApi.playerInventoryImpl().declaredButtons();
        if (!buttons.isEmpty() && ServerConfigurationNetworking.canSend(
                handler, InventoryButtonsS2C.TYPE)) {
            ServerConfigurationNetworking.send(handler,
                new InventoryButtonsS2C(buttons));
        }
        LOGGER.debug("Sent {} extra inventory slot group(s) during config phase", groups.size());
    }

    private static void sendConfigPhaseBlockTints(
            ServerConfigurationPacketListenerImpl handler) {
        var impl = PandoricalApi.blockTintsImpl();
        if (!impl.hasEntries()) return;
        ServerConfigurationNetworking.send(handler, impl.buildPacket());
        LOGGER.debug("Sent {} block tint group(s) during config phase", impl.buildPacket().entries().size());
    }

    private void registerStructureTracking() {
        EntityTrackingEvents.START_TRACKING.register(
            (entity, player) -> {
                PandoricalApi.structuresImpl().handleStartTracking(entity, player);
                PandoricalApi.entityOverlaysImpl().handleStartTracking(entity, player);
            });
        EntityTrackingEvents.STOP_TRACKING.register(
            (entity, player) -> PandoricalApi.structuresImpl().handleStopTracking(entity, player));
        // Overlay state does not persist; the owning mod sets it again on load.
        ServerEntityEvents.ENTITY_UNLOAD.register(
            (entity, world) -> PandoricalApi.entityOverlaysImpl().handleEntityUnload(entity));
    }

    private static void sendEntityRenderers(ServerPlayer player) {
        Map<String, String> renderers = EntityRendererRegistry.getAll();
        if (renderers.isEmpty()) return;

        ServerPlayNetworking.send(player, new EntityRenderersS2C(new HashMap<>(renderers)));
        LOGGER.debug("Sent {} entity renderer mapping(s) to {}", renderers.size(), player.getName().getString());
    }
}
