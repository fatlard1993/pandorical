package justfatlard.pandorical;

import justfatlard.pandorical.api.EntityRendererRegistry;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.PlayerInventoryApi;
import justfatlard.pandorical.api.PlayerInventoryApiImpl;
import justfatlard.pandorical.config.ConfigPatience;
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
    /**
     * The wire format, bumped whenever what goes over it changes shape.
     *
     * <p>v5 because an item entry now carries whether the item is food, and what kind. A v4 client
     * decoding a v5 entry stops one field short and reads the next item's id as this one's, so the
     * whole content sync comes apart - the change is a new field in the middle of a stream, which
     * is never something an older reader can skip.
     *
     * <p>v4 because v3 covered two incompatible content formats: an equippable item's slot gained
     * the id of its armour asset partway through and this number did not move, so a server and a
     * client could agree they were both speaking v3 and disagree about every piece of armour. The
     * number is only worth having if it is bumped, and the check below is only worth having if
     * the number is honest.
     */
    public static final int PROTOCOL_VERSION = 15;

    /**
     * The oldest client this server can still be understood by.
     *
     * <p>Eleven, because v11 added two fields to every block entry and v10 put two varints in the
     * middle of the config content payload. Either one shifts everything after it, so an older
     * client does not read a slightly wrong packet - it reads garbage and fails somewhere
     * unrelated. There is no reading past these.
     *
     * <p>It was five for a long while, and the reasoning is worth keeping: v6 only added a payload
     * type nobody older asks for, which is the additive case this floor is meant to allow. A
     * version bump is not automatically a compatibility break; a change to the shape of an existing
     * payload is.
     */
    public static final int MINIMUM_PROTOCOL = 11;

    /** What the jar calls itself, so a refusal can name the version to go and install. */
    public static String modVersion() {
        return net.fabricmc.loader.api.FabricLoader.getInstance()
            .getModContainer(MOD_ID)
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");
    }

    public static final List<String> SERVER_CAPABILITIES = List.of("screens", "content", "camera", "hud", "structures", "entity_overlays", "chest_overlays", "keybinds", "hud_elements", "skins", "render_policy", "animations", "mount_policy", "settings", "block_marks");

    /**
     * Tracks player UUIDs (from GameProfile) that completed config-phase content sync.
     * Entries are consumed (removed) when the player transitions to play phase.
     *
     * <p>An id left here by a connection that never reached the play phase is harmless: the next
     * attempt acks on a new connection, re-adds it, and joins, and the join is what clears it.
     * That was NOT true while this set also served as the guard against a second acknowledgement,
     * which made one successful sync a permanent fact about the player - a connection that died
     * between the ack and the join left the id behind, every later attempt was read as a repeat
     * and ignored, the task was never completed, and that player hung on "Joining world" forever,
     * on any client, until the server restarted. The repeat guard is per-connection now, which is
     * the thing it was always describing.
     */
    private static final Set<java.util.UUID> configPhaseSyncedPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Connections that have already acknowledged, so a second ack on the SAME connection is
     * ignored rather than completing a task that is no longer there.
     */
    private static final Set<Object> ackedConfigConnections = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @SuppressWarnings("unchecked")
    public static final MenuType<PandoricalMenu> MENU_TYPE = (MenuType<PandoricalMenu>) Registry.register(
        BuiltInRegistries.MENU,
        Identifier.fromNamespaceAndPath(MOD_ID, "container"),
        new MenuType<>(PandoricalMenu::new, FeatureFlags.VANILLA_SET)
    );


    @Override
    public void onInitialize() {
        // Auto-detect and register all non-system mod namespaces as server-only.
        // Only on dedicated server; on the client this would incorrectly filter everything.
        if (net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType() == net.fabricmc.api.EnvType.SERVER) {
            autoRegisterServerOnlyNamespaces();
            PandoricalApi.contentRegistry().autoScanAllModAssets();
        }

        registerPayloads();
        registerConfigPhase();
        registerServerHandlers();
        registerStructureTracking();

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
        PayloadTypeRegistry.clientboundConfiguration().register(PlayerInventoryRegistrationsS2C.TYPE, PlayerInventoryRegistrationsS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundConfiguration().register(
            justfatlard.pandorical.protocol.RequirementS2C.TYPE,
            justfatlard.pandorical.protocol.RequirementS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundConfiguration().register(
            justfatlard.pandorical.protocol.InventoryButtonsS2C.TYPE,
            justfatlard.pandorical.protocol.InventoryButtonsS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundConfiguration().register(BlockTintsConfigS2C.TYPE, BlockTintsConfigS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundConfiguration().register(
            justfatlard.pandorical.protocol.KeepsakesAskConfigS2C.TYPE,
            justfatlard.pandorical.protocol.KeepsakesAskConfigS2C.STREAM_CODEC);
        // C2S config
        PayloadTypeRegistry.serverboundConfiguration().register(ContentReadyConfigC2S.TYPE, ContentReadyConfigC2S.STREAM_CODEC);
        PayloadTypeRegistry.serverboundConfiguration().register(
            justfatlard.pandorical.protocol.KeepsakesConfigC2S.TYPE,
            justfatlard.pandorical.protocol.KeepsakesConfigC2S.STREAM_CODEC);

        // --- Play phase ---
        // S2C play
        PayloadTypeRegistry.clientboundPlay().register(HelloS2C.TYPE, HelloS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
            justfatlard.pandorical.protocol.KeepsakeStoreS2C.TYPE,
            justfatlard.pandorical.protocol.KeepsakeStoreS2C.STREAM_CODEC);
        // Also in play, so a button that is a switch can change its face while somebody watches.
        PayloadTypeRegistry.clientboundPlay().register(
            justfatlard.pandorical.protocol.InventoryButtonsS2C.TYPE,
            justfatlard.pandorical.protocol.InventoryButtonsS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
            justfatlard.pandorical.protocol.BlockTintPositionsS2C.TYPE,
            justfatlard.pandorical.protocol.BlockTintPositionsS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
            justfatlard.pandorical.protocol.BlockMarksS2C.TYPE,
            justfatlard.pandorical.protocol.BlockMarksS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
            justfatlard.pandorical.protocol.BannerDecalsS2C.TYPE,
            justfatlard.pandorical.protocol.BannerDecalsS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
            justfatlard.pandorical.protocol.ClientSettingS2C.TYPE,
            justfatlard.pandorical.protocol.ClientSettingS2C.STREAM_CODEC);
        // Large, because a screen can carry a mod's readme, and a readme is more than a form.
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
        // An entity that has gone is not playing anything. Without this the table of what is
        // playing only ever grows: every fish, every companion, every mob that ever animated stays
        // in it for the life of the server, and each one is re-sent to every player who joins.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_UNLOAD.register(
            (entity, level) -> PandoricalApi.AnimationApiImpl.forget(entity.getId()));

        PayloadTypeRegistry.clientboundPlay().register(
            justfatlard.pandorical.protocol.MountPolicyS2C.TYPE,
            justfatlard.pandorical.protocol.MountPolicyS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
            justfatlard.pandorical.protocol.PlayAnimationS2C.TYPE,
            justfatlard.pandorical.protocol.PlayAnimationS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
            justfatlard.pandorical.protocol.RenderPolicyS2C.TYPE,
            justfatlard.pandorical.protocol.RenderPolicyS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().registerLarge(
            justfatlard.pandorical.protocol.SkinOverrideS2C.TYPE,
            justfatlard.pandorical.protocol.SkinOverrideS2C.STREAM_CODEC, 1048576);
        PayloadTypeRegistry.clientboundPlay().register(EntityRenderersS2C.TYPE, EntityRenderersS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SpawnStructureS2C.TYPE, SpawnStructureS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(UpdateStructurePoseS2C.TYPE, UpdateStructurePoseS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(UpdateStructureBlocksS2C.TYPE, UpdateStructureBlocksS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SetStructureVisibleS2C.TYPE, SetStructureVisibleS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(DespawnStructureS2C.TYPE, DespawnStructureS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(EntityOverlayS2C.TYPE, EntityOverlayS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
            justfatlard.pandorical.protocol.ChestOverlayS2C.TYPE,
            justfatlard.pandorical.protocol.ChestOverlayS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(KeybindDeclarationsS2C.TYPE, KeybindDeclarationsS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
            justfatlard.pandorical.protocol.KeybindRebindS2C.TYPE,
            justfatlard.pandorical.protocol.KeybindRebindS2C.STREAM_CODEC);

        // C2S play
        PayloadTypeRegistry.serverboundPlay().register(HelloC2S.TYPE, HelloC2S.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ScreenActionC2S.TYPE, ScreenActionC2S.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ContentReadyC2S.TYPE, ContentReadyC2S.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(KeyPressC2S.TYPE, KeyPressC2S.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
            justfatlard.pandorical.protocol.KeybindBindingsC2S.TYPE,
            justfatlard.pandorical.protocol.KeybindBindingsC2S.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
            justfatlard.pandorical.protocol.KeyReleaseC2S.TYPE, justfatlard.pandorical.protocol.KeyReleaseC2S.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
            justfatlard.pandorical.protocol.InventoryButtonC2S.TYPE,
            justfatlard.pandorical.protocol.InventoryButtonC2S.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
            justfatlard.pandorical.protocol.OpenSettingsC2S.TYPE,
            justfatlard.pandorical.protocol.OpenSettingsC2S.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
            justfatlard.pandorical.protocol.ClientSettingsC2S.TYPE,
            justfatlard.pandorical.protocol.ClientSettingsC2S.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
            justfatlard.pandorical.protocol.ViewportC2S.TYPE,
            justfatlard.pandorical.protocol.ViewportC2S.STREAM_CODEC);
    }

    /**
     * Register configuration-phase task and handlers.
     * Content sync happens here BEFORE Fabric's SynchronizeRegistriesTask.
     */
    private void registerConfigPhase() {
        // Keepsakes: asked for at login, and the login waits for the answer.
        ServerConfigurationNetworking.registerGlobalReceiver(
            justfatlard.pandorical.protocol.KeepsakesConfigC2S.TYPE, (payload, context) -> {
                var handler = context.packetListener();
                context.server().execute(() ->
                    justfatlard.pandorical.config.Keepsakes.INSTANCE.answered(handler, payload));
            });
        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
            if (justfatlard.pandorical.config.Keepsakes.INSTANCE.askable(handler)) {
                handler.addTask(new justfatlard.pandorical.config.Keepsakes.Task());
            }
        });

        // Server: handle client acknowledgment during config phase
        ServerConfigurationNetworking.registerGlobalReceiver(ContentReadyConfigC2S.TYPE, (payload, context) -> {
            var handler = context.packetListener();

            // completeTask starts the next configuration task, which is vanilla's
            // SynchronizeRegistriesTask reading server registries. That belongs on the
            // server thread, like every other handler here.
            context.server().execute(() -> {
                var profile = handler.getOwner();

                // Only the first ack on THIS connection may complete the task. A second one
                // finds the task already gone and throws, and on this thread nothing catches
                // it: running here means a client that acks twice takes the server down rather
                // than just itself. Keyed on the connection and not the player, because the
                // same player reconnecting is a new connection with a task of its own waiting
                // to be completed - keyed on the player, their second visit hangs forever.
                if (!ackedConfigConnections.add(handler)) {
                    LOGGER.debug("Ignoring repeat config-phase ack from {}",
                        profile != null ? profile.name() : "(unknown profile)");
                    return;
                }
                if (profile != null) configPhaseSyncedPlayers.add(profile.id());
                ConfigPatience.end(handler, ((justfatlard.pandorical.mixin.ServerCommonConnectionAccessor) handler).pandorical$connection());
                LOGGER.info("Client {} completed config-phase content sync",
                    profile != null ? profile.name() : "(unknown profile)");

                // The client has registered every synced block now, so a tint can find its
                // block. Sent any earlier it named blocks that did not exist yet and was
                // dropped on arrival, and no synced block was ever coloured on a first join.
                sendConfigPhaseBlockTints(handler);

                try {
                    handler.completeTask(PandoricalSyncTask.TYPE);
                } catch (IllegalStateException e) {
                    // Belt and braces: nothing a peer sends should be able to reach the
                    // server thread with an uncaught throw.
                    LOGGER.warn("Could not complete config-phase task: {}", e.getMessage());
                }
            });
        });

        // A finished connection is not a connection anybody can ack on again. Only the
        // per-connection entry is dropped here: whether this event also fires on the ordinary
        // hand-off into the play phase is not something the API says plainly, and clearing the
        // player's id on that path would take the flag away before JOIN reads it. It does not
        // need clearing anyway - see configPhaseSyncedPlayers.
        ServerConfigurationConnectionEvents.DISCONNECT.register((handler, server) -> {
            ackedConfigConnections.remove(handler);
            ConfigPatience.forget(handler);
        });

        // Server: add our sync task BEFORE Fabric's registry sync
        ServerConfigurationConnectionEvents.BEFORE_CONFIGURE.register((handler, server) -> {
            if (!agreeOnVersion(handler)) return;

            if (ServerConfigurationNetworking.canSend(handler, SyncContentConfigS2C.TYPE)) {
                // Send inventory slot registrations during config phase so the client
                // has them BEFORE InventoryMenu is constructed on play-phase entry.
                sendConfigPhaseInventoryRegistrations(handler);
                // Not the block tints: those name blocks the client has yet to register, and
                // a tint for a block it cannot find is dropped. They go once it says it has them.

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
                        // BEFORE_CONFIGURE fires before vanilla populates the task queue, so
                        // this queue is empty here and an ordinary addTask would already run
                        // first. The front-insertion is insurance for the day that stops being
                        // true: content has to reach the client before the registry sync that
                        // assigns its IDs.
                        var task = new PandoricalSyncTask(blocks, items, assetChunks,
                            entityTypes, blockEntityTypes, villagerProfessions,
                            poiTypes, menuTypes, recipeBookCategories, contentRegistry.railsSolid());
                        try {
                            var field = net.minecraft.server.network.ServerConfigurationPacketListenerImpl.class
                                .getDeclaredField("configurationTasks");
                            field.setAccessible(true);
                            @SuppressWarnings("unchecked")
                            var queue = (java.util.Queue<net.minecraft.server.network.ConfigurationTask>) field.get(handler);
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
                        ConfigPatience.begin(handler,
                            ((justfatlard.pandorical.mixin.ServerCommonConnectionAccessor) handler).pandorical$connection());
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
                        && PandoricalApi.hasCapability(player, "content")
                        && !PandoricalApi.isContentReady(player)
                        && PandoricalApi.beginContentSync(player.getUUID())) {
                    contentRegistry.syncContentTo(player);
                }

                sendEntityRenderers(player);
                // On join, entity tracking starts before this handshake
                // completes, so replay overlays for already-tracked entities
                PandoricalApi.entityOverlaysImpl().handlePlayerReady(player);
                PandoricalApi.keybindsImpl().handlePlayerReady(player);
                // And the same moment for every consuming mod's own replays
                // Everyone already wearing an override, before anything else is drawn: a skin is a
                // state and not an event, so a client that missed the announcement would see that
                // person as Steve for as long as both stayed logged in.
                PandoricalApi.SkinApiImpl.sendAllTo(player);
                PandoricalApi.RenderApiImpl.sendTo(player);
                PandoricalApi.AnimationApiImpl.sendAllTo(player);
                PandoricalApi.MountApiImpl.sendTo(player);

                PandoricalApi.firePlayerReady(player);
            });
        });

        // Pooled keybind presses: all validation (capability, slot, rate
        // limit) happens inside handleKeyPress, on the server thread
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(
            server -> justfatlard.pandorical.settings.ModCommands.forget());
        ServerPlayNetworking.registerGlobalReceiver(
            justfatlard.pandorical.protocol.KeybindBindingsC2S.TYPE, (payload, context) -> {
                context.player().level().getServer().execute(() ->
                    PandoricalApi.keybindsImpl().handleBindings(context.player(), payload.keys()));
            });
        ServerPlayNetworking.registerGlobalReceiver(KeyPressC2S.TYPE, (payload, context) -> {
            context.server().execute(() ->
                PandoricalApi.keybindsImpl().handleKeyPress(context.player(), payload.slot()));
        });
        ServerPlayNetworking.registerGlobalReceiver(
            justfatlard.pandorical.protocol.KeyReleaseC2S.TYPE, (payload, context) -> {
                context.server().execute(() ->
                    PandoricalApi.keybindsImpl().handleKeyRelease(context.player(), payload.slot()));
            });

        ServerPlayNetworking.registerGlobalReceiver(
            justfatlard.pandorical.protocol.InventoryButtonC2S.TYPE, (payload, context) -> {
                context.server().execute(() -> PandoricalApi.playerInventoryImpl()
                    .handleButton(context.player(), payload.namespace(), payload.id()));
            });

        ServerPlayNetworking.registerGlobalReceiver(
            justfatlard.pandorical.protocol.OpenSettingsC2S.TYPE, (payload, context) -> {
                context.server().execute(() -> PandoricalApi.settings().open(context.player()));
            });
        ServerPlayNetworking.registerGlobalReceiver(
            justfatlard.pandorical.protocol.ClientSettingsC2S.TYPE, (payload, context) -> {
                context.server().execute(() ->
                    justfatlard.pandorical.settings.ClientMods.declare(context.player(), payload));
            });
        ServerPlayNetworking.registerGlobalReceiver(
            justfatlard.pandorical.protocol.ViewportC2S.TYPE, (payload, context) -> {
                context.server().execute(() ->
                    justfatlard.pandorical.screen.Viewport.declare(context.player(), payload));
            });
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            justfatlard.pandorical.settings.ClientMods.forget(handler.player);
            justfatlard.pandorical.screen.Viewport.forget(handler.player);
        });

        PandoricalApi.settingsImpl().init();
        PandoricalApi.onPlayerReady(player -> PandoricalApi.blockMarksImpl().sendAll(player));
        net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register(
            (player, origin, destination) -> PandoricalApi.blockMarksImpl().sendAll(player));
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register(
            (dispatcher, registry, environment) ->
                justfatlard.pandorical.settings.SettingsCommand.register(dispatcher, PandoricalApi.settingsImpl()));

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

            // Track config-phase players for later; don't pre-register capabilities yet
            // because the client can't deserialize full component data (armor materials, etc.)
            // until after the handshake. Capabilities are registered on HelloC2S arrival.
            var player = handler.getPlayer();
            if (configPhaseSyncedPlayers.remove(player.getGameProfile().id())) {
                PandoricalApi.markContentReady(player.getUUID());
                LOGGER.debug("Player {} completed config-phase sync — awaiting HelloC2S for full inventory", player.getName().getString());
            }

            // The menu's copy of the extra slots predates the player-data load; refresh it.
            PandoricalApi.playerInventoryImpl().syncMenuFromAttachment(player);
        });

        // Respawn has the same stale-copy shape as join: the new ServerPlayer's menu is
        // built in its constructor, before restoreFrom copies the attachment over.
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register(
            (oldPlayer, newPlayer, alive) ->
                PandoricalApi.playerInventoryImpl().syncMenuFromAttachment(newPlayer));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PandoricalApi.removePlayer(handler.getPlayer().getUUID());
            // Otherwise the worn-skin table keeps a row per player who ever wore one, for the life
            // of the server, and hands every new arrival a wardrobe of people who are not here.
            PandoricalApi.SkinApiImpl.forget(handler.getPlayer().getUUID());
            justfatlard.pandorical.config.Keepsakes.INSTANCE.forget(handler.getPlayer().getUUID());
        });
    }

    /**
     * Send all registered extra inventory slot groups during the configuration phase.
     * This ensures {@code ClientInventorySlotRegistry} is populated BEFORE the client
     * constructs {@code InventoryMenu} on play-phase entry, avoiding the
     * {@code IndexOutOfBoundsException} caused by mismatched slot counts.
     */
    /**
     * Settle whether this client can be talked to before anything is said to it.
     *
     * <p>Three kinds of client arrive here. A vanilla one cannot receive the content sync at all,
     * has never been sent any, and is let through untouched - that is the whole promise of this
     * mod and nothing here may break it. A current one is told what this server speaks and
     * carries on. Between them is the one this exists for: Pandorical installed, old enough that
     * the content about to be sent will throw on the way in.
     *
     * <p>That one is told, in the disconnect box, which version it has and which to install.
     * Before this it read the content, threw inside its own config phase and closed the
     * connection with no reason given, which looks from the outside like the server rejecting
     * you over whichever mod happened to be first in the list.
     *
     * @return false if the client was turned away and nothing more should be sent to it
     */
    private static boolean agreeOnVersion(
            net.minecraft.server.network.ServerConfigurationPacketListenerImpl handler) {
        // No Pandorical at all: not our business, and never was.
        if (!ServerConfigurationNetworking.canSend(handler, SyncContentConfigS2C.TYPE)) return true;

        // No exemption for a content-free server any more. That carve-out was written when the
        // only thing that had changed shape was the content sync; the screen payload has since
        // grown a field of its own, so an old client would misread the first screen it was sent
        // whether or not this server has any blocks to give it. Anything speaking this protocol
        // at all has to be current.

        if (ServerConfigurationNetworking.canSend(handler,
                justfatlard.pandorical.protocol.RequirementS2C.TYPE)) {
            ServerConfigurationNetworking.send(handler,
                new justfatlard.pandorical.protocol.RequirementS2C(
                    PROTOCOL_VERSION, MINIMUM_PROTOCOL, modVersion()));
            return true;
        }

        // It cannot even be told what is wrong with it, which is itself the answer: this type has
        // existed for as long as the current content format has.
        String needed = modVersion();
        LOGGER.warn("Refused a client running a Pandorical older than {}: it cannot read this"
            + " server's content format", needed);
        handler.disconnect(net.minecraft.network.chat.Component.literal(
            "Your Pandorical is out of date.\n\n"
            + "This server needs Pandorical " + needed + " or newer.\n"
            + "Replace the pandorical jar in your mods folder and reconnect."));
        return false;
    }

    private static void sendConfigPhaseInventoryRegistrations(
            net.minecraft.server.network.ServerConfigurationPacketListenerImpl handler) {
        List<PlayerInventoryApi.SlotRegistration> regs = PandoricalApi.playerInventoryImpl().getRegistrations();
        if (regs.isEmpty()) return;

        List<PlayerInventoryRegistrationsS2C.SlotGroup> groups = new java.util.ArrayList<>();
        for (PlayerInventoryApi.SlotRegistration reg : regs) {
            List<PlayerInventoryRegistrationsS2C.SlotPosition> positions = new java.util.ArrayList<>();
            for (PlayerInventoryApi.SlotEntry entry : reg.slots()) {
                positions.add(new PlayerInventoryRegistrationsS2C.SlotPosition(
                    entry.slotIndex(), entry.screenX(), entry.screenY(), entry.backgroundSprite()));
            }
            groups.add(new PlayerInventoryRegistrationsS2C.SlotGroup(
                reg.namespace().toString(), positions));
        }

        ServerConfigurationNetworking.send(handler, new PlayerInventoryRegistrationsS2C(groups));

        // Only to a client that has said it understands them. An older one simply gets no
        // buttons, rather than a packet it cannot read.
        var buttons = PandoricalApi.playerInventoryImpl().declaredButtons();
        if (!buttons.isEmpty() && ServerConfigurationNetworking.canSend(
                handler, justfatlard.pandorical.protocol.InventoryButtonsS2C.TYPE)) {
            ServerConfigurationNetworking.send(handler,
                new justfatlard.pandorical.protocol.InventoryButtonsS2C(buttons));
        }
        LOGGER.debug("Sent {} extra inventory slot group(s) during config phase", groups.size());
    }

    private static void sendConfigPhaseBlockTints(
            net.minecraft.server.network.ServerConfigurationPacketListenerImpl handler) {
        var impl = PandoricalApi.blockTintsImpl();
        if (!impl.hasEntries()) return;
        ServerConfigurationNetworking.send(handler, impl.buildPacket());
        LOGGER.debug("Sent {} block tint group(s) during config phase", impl.buildPacket().entries().size());
    }

    /**
     * Ties structure visibility to real entity tracking: a player who starts tracking a
     * structure's anchor entity gets the structure spawned to them; a player who stops
     * tracking it (out of range, entity removed, or disconnect) gets it despawned.
     * See {@link justfatlard.pandorical.api.StructureApi} for the broadcast-scoped design.
     */
    private void registerStructureTracking() {
        net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents.START_TRACKING.register(
            (entity, player) -> {
                PandoricalApi.structuresImpl().handleStartTracking(entity, player);
                PandoricalApi.entityOverlaysImpl().handleStartTracking(entity, player);
            });
        net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents.STOP_TRACKING.register(
            (entity, player) -> PandoricalApi.structuresImpl().handleStopTracking(entity, player));
        // Overlay state does not persist: drop it when the entity unloads and
        // let the owning mod re-set it on load (see EntityOverlayApi javadoc)
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_UNLOAD.register(
            (entity, world) -> PandoricalApi.entityOverlaysImpl().handleEntityUnload(entity));
    }

    /**
     * Send all registered entity renderer mappings to the player.
     * Called after the player completes the HelloC2S handshake.
     */
    private static void sendEntityRenderers(net.minecraft.server.level.ServerPlayer player) {
        java.util.Map<String, String> renderers = EntityRendererRegistry.getAll();
        if (renderers.isEmpty()) return;

        ServerPlayNetworking.send(player, new EntityRenderersS2C(new java.util.HashMap<>(renderers)));
        LOGGER.debug("Sent {} entity renderer mapping(s) to {}", renderers.size(), player.getName().getString());
    }
}
