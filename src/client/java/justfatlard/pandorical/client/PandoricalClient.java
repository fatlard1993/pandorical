package justfatlard.pandorical.client;

import justfatlard.pandorical.BlockMarkLookup;
import justfatlard.pandorical.Diagnostics;
import justfatlard.pandorical.MountPolicy;
import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.api.Capabilities;
import justfatlard.pandorical.api.NotUnderstood;
import justfatlard.pandorical.client.animation.AnimationLibrary;
import justfatlard.pandorical.client.animation.EntityAnimations;
import justfatlard.pandorical.client.api.PandoricalClientApi;
import justfatlard.pandorical.client.camera.CameraManager;
import justfatlard.pandorical.client.component.ComponentRegistry;
import justfatlard.pandorical.client.content.ContentManager;
import justfatlard.pandorical.client.contextmodel.ContextModels;
import justfatlard.pandorical.client.contextmodel.DoorBanks;
import justfatlard.pandorical.client.contextmodel.DoorJambs;
import justfatlard.pandorical.client.contextmodel.FenceGateJoins;
import justfatlard.pandorical.client.contextmodel.RailDiagonals;
import justfatlard.pandorical.client.contextmodel.SlabHung;
import justfatlard.pandorical.client.contextmodel.TrapdoorBanks;
import justfatlard.pandorical.client.decal.BannerDecalRenderer;
import justfatlard.pandorical.client.decal.BannerDecalStore;
import justfatlard.pandorical.client.diag.StackSampler;
import justfatlard.pandorical.client.hud.HudManager;
import justfatlard.pandorical.client.hud.HudRenderer;
import justfatlard.pandorical.client.hud.VanillaHudElementSuppressor;
import justfatlard.pandorical.client.inventory.ClientInventoryButtons;
import justfatlard.pandorical.client.inventory.ClientInventorySlotRegistry;
import justfatlard.pandorical.client.keepsake.ClientKeepsakes;
import justfatlard.pandorical.client.keybind.KeybindManager;
import justfatlard.pandorical.client.mixin.ClientCommonListenerAccessor;
import justfatlard.pandorical.client.maprelief.ClientMapReliefs;
import justfatlard.pandorical.client.picture.ClientPictures;
import justfatlard.pandorical.client.render.LeafCulling;
import justfatlard.pandorical.client.renderer.ChestOverlayStore;
import justfatlard.pandorical.client.renderer.ClientBlockMarks;
import justfatlard.pandorical.client.renderer.ClientEntityRendererRegistry;
import justfatlard.pandorical.client.renderer.EntityOverlayStore;
import justfatlard.pandorical.client.renderer.PositionalTintStore;
import justfatlard.pandorical.client.screen.PandoricalContainerScreen;
import justfatlard.pandorical.client.screen.PandoricalScreen;
import justfatlard.pandorical.client.settings.ClientSettings;
import justfatlard.pandorical.client.settings.ContainerHabits;
import justfatlard.pandorical.client.settings.ServerCapabilities;
import justfatlard.pandorical.client.settings.ServerSettingsButton;
import justfatlard.pandorical.client.settings.ViewportReporter;
import justfatlard.pandorical.client.skin.SkinOverrides;
import justfatlard.pandorical.client.structure.StructureManager;
import justfatlard.pandorical.client.structure.StructureRenderer;
import justfatlard.pandorical.protocol.*;
import justfatlard.pandorical.screen.PandoricalMenu;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Block;

import java.text.NumberFormat;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class PandoricalClient implements ClientModInitializer {
    private static final List<String> CLIENT_CAPABILITIES = Capabilities.CLIENT;

    // The newest def is the last in insertion order.
    private static final Map<String, OpenScreenS2C> pendingContainerDefs = new LinkedHashMap<>();

    /** Startup pieces {@code -Dpandorical.skip=a,b,...} leaves out, to bisect a startup crash. */
    private static final Set<String> SKIP = Arrays.stream(
            System.getProperty("pandorical.skip", "").split(","))
        .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    private static final Set<String> SKIPPABLE = Set.of(
        "keybinds", "contextmodels", "suppressor", "hud", "structures", "decals", "pictures", "maprelief", "all");

    private static boolean skipped(String piece) {
        Diagnostics.mark("startup: " + piece);
        boolean skip = SKIP.contains(piece) || SKIP.contains("all");
        if (skip) Pandorical.LOGGER.warn("[pandorical] diagnostic: leaving out {}", piece);
        return skip;
    }

    @Override
    public void onInitializeClient() {
        for (String piece : SKIP) {
            if (!SKIPPABLE.contains(piece)) Pandorical.LOGGER.warn("[pandorical] diagnostic: pandorical.skip names {}, which is not one of {}", piece, SKIPPABLE);
        }
        StackSampler.start();
        Diagnostics.mark("client init begins");
        // The load guard is raised again for every join, from the first configuration packet.
        ClientConfigurationConnectionEvents.INIT.register((handler, client) -> {
            Diagnostics.guardFor(Diagnostics.JOIN_WINDOW_MILLIS);
            StackSampler.start();
        });
        if (Diagnostics.windowsClient()) {
            PandoricalClientApi.settings().group(Pandorical.MOD_ID, "Pandorical")
                .toggle("loadGuard", "Load guard",
                    "Loads a little slower on Windows, to dodge a crash some Windows players get while loading",
                    Diagnostics::guarding, Diagnostics::setGuarding);
        }
        BlockMarkLookup.client = ClientBlockMarks::has;
        ContainerHabits.register();
        justfatlard.pandorical.client.actions.ActionMenus.register();
        ComponentRegistry.registerDefaults();

        // Vanilla's MenuType factory gets only a sync id and an inventory, so the slot count
        // comes from the definition that arrived just before it.
        PandoricalMenu.setIncomingModSlots(() -> {
            OpenScreenS2C newest = null;
            for (var entry : pendingContainerDefs.entrySet()) newest = entry.getValue();
            return newest == null ? -1 : newest.container().map(c -> c.slotCount()).orElse(-1);
        });

        if (!skipped("keybinds")) KeybindManager.init();
        if (!skipped("contextmodels")) {
            ContextModels.register(new RailDiagonals());
            ContextModels.register(new FenceGateJoins());
            ContextModels.register(new DoorBanks());
            ContextModels.register(new TrapdoorBanks());
            ContextModels.register(new DoorJambs());
            ContextModels.register(new SlabHung());
            ContextModels.init();
        }

        if (!skipped("suppressor")) VanillaHudElementSuppressor.init();

        // ItemCountRendererMixin abbreviates the slot label of an oversized stack.
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            int count = stack.getCount();
            if (count >= 100) {
                lines.add(Component.literal(
                        "Count: " + NumberFormat.getNumberInstance(Locale.US).format(count))
                    .withStyle(ChatFormatting.GRAY));
            }
        });

        MenuScreens.register(Pandorical.MENU_TYPE, PandoricalClient::createContainerScreen);

        registerConfigPhaseReceivers();
        registerClientHandlers();

        if (!skipped("hud")) HudRenderer.register();
        if (!skipped("structures")) StructureRenderer.register();
        if (!skipped("decals")) BannerDecalRenderer.register();
        if (!skipped("pictures")) ClientPictures.register();
        if (!skipped("maprelief")) ClientMapReliefs.register();

        ClientTickEvents.START_CLIENT_TICK.register(client -> StructureManager.tick());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ContentManager.tick();
            HudManager.tick();
            KeybindManager.tick(client);
            ViewportReporter.tick(client);
            if (ContentManager.isSyncing() && client.gui != null) {
                client.gui.hud.setTitle(Component.literal(ContentManager.getSyncStatus())
                    .withStyle(ChatFormatting.GOLD));
                client.gui.hud.setTimes(0, 40, 10);
            }
        });

        Pandorical.LOGGER.info("Pandorical client initialized");
    }

    private static PandoricalContainerScreen createContainerScreen(
            PandoricalMenu menu, Inventory inventory, Component title) {
        OpenScreenS2C screenDef = null;
        String foundKey = null;

        for (var entry : pendingContainerDefs.entrySet()) {
            screenDef = entry.getValue();
            foundKey = entry.getKey();
        }
        if (foundKey != null) {
            pendingContainerDefs.remove(foundKey);
            menu.setScreenDef(screenDef);
        } else {
            Pandorical.LOGGER.warn("No pending screen definition found for container screen — " +
                "the OpenScreenS2C packet may not have arrived before the vanilla menu open");
        }
        return new PandoricalContainerScreen(menu, inventory, title);
    }

    /**
     * These run on the network thread before Fabric's registry sync, so the blocks and items
     * registered here are ones it sees.
     */
    private void registerConfigPhaseReceivers() {
        ClientConfigurationNetworking.registerGlobalReceiver(SyncContentConfigS2C.TYPE, (payload, context) -> {
            Pandorical.LOGGER.info("Config phase: received content sync — {} blocks, {} items, {} expected asset chunks",
                payload.blocks().size(), payload.items().size(), payload.expectedAssetChunks());
            ContentManager.handleConfigSyncContent(payload);
        });

        ClientConfigurationNetworking.registerGlobalReceiver(SyncAssetsConfigS2C.TYPE, (payload, context) -> {
            Pandorical.LOGGER.debug("Config phase: received asset chunk {}/{}",
                payload.chunkIndex() + 1, payload.totalChunks());
            ContentManager.handleConfigSyncAssets(payload);
        });

        // In the configuration phase: InventoryMenu is built before any play packet arrives.
        ClientConfigurationNetworking.registerGlobalReceiver(PlayerInventoryRegistrationsS2C.TYPE, (payload, context) -> {
            Pandorical.LOGGER.debug("Config phase: received {} extra inventory slot group(s)", payload.groups().size());
            ClientInventorySlotRegistry.receive(payload);
        });

        ClientConfigurationNetworking.registerGlobalReceiver(
            InventoryButtonsS2C.TYPE, (payload, context) -> {
                ClientInventoryButtons.set(payload.buttons());
                Pandorical.LOGGER.debug("Inventory buttons received: {}", payload.buttons().size());
            });

        ClientPlayNetworking.registerGlobalReceiver(
            InventoryButtonsS2C.TYPE, (payload, context) ->
                ClientInventoryButtons.set(payload.buttons()));

        // Required: the server refuses a client that cannot receive this, and Fabric advertises
        // a channel only when a receiver is registered.
        ClientConfigurationNetworking.registerGlobalReceiver(
            RequirementS2C.TYPE, (payload, context) -> {
                if (Pandorical.PROTOCOL_VERSION < payload.minimumProtocol()) {
                    // Saying so beats the decode error that arrives a packet later and names nothing.
                    Pandorical.LOGGER.warn("Server needs Pandorical {} (protocol v{}); this client speaks v{}",
                        payload.serverModVersion(), payload.minimumProtocol(), Pandorical.PROTOCOL_VERSION);
                    var connection = ((ClientCommonListenerAccessor) context.packetListener()).pandorical$connection();
                    context.client().execute(() -> connection.disconnect(Component.translatable(
                        "pandorical.too_old", payload.serverModVersion())));
                } else {
                    Pandorical.LOGGER.debug("Server runs Pandorical {} — protocol v{}, minimum v{}",
                        payload.serverModVersion(), payload.protocolVersion(), payload.minimumProtocol());
                }
            });

        ClientConfigurationNetworking.registerGlobalReceiver(BlockTintsConfigS2C.TYPE, (payload, context) -> {
            Pandorical.LOGGER.debug("Config phase: received {} block tint group(s)", payload.entries().size());
            PositionalTintStore.clear();
            BannerDecalStore.clear();
            payload.entries().forEach(PandoricalClient::applyBlockTints);
        });
    }

    private static void applyBlockTints(BlockTintsConfigS2C.Entry entry) {
        BlockTintSource source = switch (entry.tintType()) {
            case "grass"     -> BlockTintSources.grass();
            case "stem"      -> BlockTintSources.stem();
            case "sugar_cane"-> BlockTintSources.sugarCane();
            case "foliage"   -> BlockTintSources.foliage();
            case "constant"  -> BlockTintSources.constant(entry.constantColor());
            case "positional"-> PositionalTintStore.source(entry.constantColor());
            default -> {
                ClientNotices.report(NotUnderstood.TINT_TYPE, entry.tintType());
                yield null;
            }
        };
        if (source == null) return;

        Block[] blocks = entry.blockIds().stream()
            .map(id -> BuiltInRegistries.BLOCK.getValue(Identifier.parse(id)))
            .filter(Objects::nonNull)
            .toArray(Block[]::new);
        if (blocks.length > 0) BlockColorRegistry.register(List.of(source), blocks);
        if (blocks.length > 0 && "positional".equals(entry.tintType())) {
            PositionalTintStore.track(blocks);
        }
    }

    private void registerClientHandlers() {
        ClientPlayNetworking.registerGlobalReceiver(
            BlockMarksS2C.TYPE, (payload, context) ->
                context.client().execute(() -> ClientBlockMarks.apply(payload)));
        ClientPlayNetworking.registerGlobalReceiver(
            BlockTintPositionsS2C.TYPE, (payload, context) ->
                context.client().execute(() -> payload.entries().forEach(entry ->
                    PositionalTintStore.paint(
                        entry.pos(), entry.argb()))));
        ClientPlayNetworking.registerGlobalReceiver(
            ClientSettingS2C.TYPE, (payload, context) ->
                context.client().execute(() -> ClientSettings.INSTANCE.apply(payload)));
        ClientPlayNetworking.registerGlobalReceiver(
            BannerDecalsS2C.TYPE, (payload, context) ->
                context.client().execute(() -> BannerDecalStore.apply(payload)));

        ClientKeepsakes.register();

        ClientPlayNetworking.registerGlobalReceiver(HelloS2C.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (payload.protocolVersion() != Pandorical.PROTOCOL_VERSION) {
                    Pandorical.LOGGER.warn("Server has Pandorical protocol v{} (client is v{}) — features may not work correctly",
                        payload.protocolVersion(), Pandorical.PROTOCOL_VERSION);
                }
                Pandorical.LOGGER.debug("Server hello received, protocol v{}, capabilities: {}",
                    payload.protocolVersion(), payload.capabilities());
                ServerCapabilities.set(payload.capabilities());
                ClientPlayNetworking.send(new HelloC2S(Pandorical.PROTOCOL_VERSION, CLIENT_CAPABILITIES));
                ClientSettings.INSTANCE.send();
                // Anything the configuration phase could not read has been waiting to be sent.
                ClientNotices.flush();
                // What is in the mods folder, including the jars the loader skipped.
                justfatlard.pandorical.client.settings.ClientModFiles.report();
                ViewportReporter.send(context.client());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(OpenScreenS2C.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (payload.container().isPresent()) {
                    // The vanilla menu open arrives next.
                    pendingContainerDefs.put(payload.screenId(), payload);
                } else {
                    PandoricalScreen screen = new PandoricalScreen(payload);
                    Minecraft.getInstance().gui.setScreen(screen);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(UpdateScreenS2C.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                Screen current = Minecraft.getInstance().gui.screen();
                if (current instanceof PandoricalScreen ps && ps.getScreenId().equals(payload.screenId())) {
                    ps.applyUpdates(payload.updates());
                } else if (current instanceof PandoricalContainerScreen pcs && payload.screenId().equals(pcs.getScreenId())) {
                    pcs.applyUpdates(payload.updates());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(CloseScreenS2C.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                Screen current = Minecraft.getInstance().gui.screen();
                if (current instanceof PandoricalScreen ps && ps.getScreenId().equals(payload.screenId())) {
                    Minecraft.getInstance().gui.setScreen(null);
                } else if (current instanceof PandoricalContainerScreen pcs && payload.screenId().equals(pcs.getScreenId())) {
                    Minecraft.getInstance().gui.setScreen(null);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ShowHudS2C.TYPE, (payload, context) -> {
            context.client().execute(() -> HudManager.handleShow(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(UpdateHudS2C.TYPE, (payload, context) -> {
            context.client().execute(() -> HudManager.handleUpdate(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(HideHudS2C.TYPE, (payload, context) -> {
            context.client().execute(() -> HudManager.handleHide(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(SetVanillaHudElementsS2C.TYPE, (payload, context) -> {
            context.client().execute(() ->
                VanillaHudElementSuppressor.handle(payload));
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncContentS2C.TYPE, (payload, context) -> {
            context.client().execute(() -> ContentManager.handleSyncContent(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(SyncAssetsS2C.TYPE, (payload, context) -> {
            context.client().execute(() -> ContentManager.handleSyncAssets(payload));
        });

        ClientPlayNetworking.registerGlobalReceiver(CameraHintS2C.TYPE, (payload, context) -> {
            context.client().execute(() -> CameraManager.handleHint(payload));
        });

        ResourceManagerHelper
            .get(PackType.CLIENT_RESOURCES)
            .registerReloadListener(new AnimationLibrary());

        ClientPlayNetworking.registerGlobalReceiver(
            MountPolicyS2C.TYPE, (payload, context) -> {
                context.client().execute(() -> MountPolicy.set(
                    payload.doubleRiders(), payload.freeLook()));
            });

        ClientPlayNetworking.registerGlobalReceiver(
            PlayAnimationS2C.TYPE, (payload, context) -> {
                context.client().execute(() -> {
                    if (payload.animation().isEmpty()) {
                        EntityAnimations.stop(payload.entityId());
                    } else {
                        EntityAnimations.play(
                            payload.entityId(),
                            Identifier.parse(payload.animation()),
                            payload.looping());
                    }
                });
            });

        ClientPlayNetworking.registerGlobalReceiver(
            RenderPolicyS2C.TYPE, (payload, context) -> {
                context.client().execute(() ->
                    LeafCulling.setEnforced(payload.cullLeaves()));
            });

        ClientPlayNetworking.registerGlobalReceiver(
            SkinOverrideS2C.TYPE, (payload, context) -> {
                context.client().execute(() ->
                    SkinOverrides.handle(payload));
            });

        ClientPlayNetworking.registerGlobalReceiver(EntityRenderersS2C.TYPE, (payload, context) -> {
            context.client().execute(() -> ClientEntityRendererRegistry.applyRenderers(payload));
        });

        ClientPlayNetworking.registerGlobalReceiver(SpawnStructureS2C.TYPE, (payload, context) -> {
            context.client().execute(() -> StructureManager.handleSpawn(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(UpdateStructurePoseS2C.TYPE, (payload, context) -> {
            context.client().execute(() -> StructureManager.handleUpdatePose(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(UpdateStructureBlocksS2C.TYPE, (payload, context) -> {
            context.client().execute(() -> StructureManager.handleUpdateBlocks(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(SetStructureVisibleS2C.TYPE, (payload, context) -> {
            context.client().execute(() -> StructureManager.handleSetVisible(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(SetStructureWalkableS2C.TYPE, (payload, context) -> {
            context.client().execute(() -> StructureManager.handleSetWalkable(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(DespawnStructureS2C.TYPE, (payload, context) -> {
            context.client().execute(() -> StructureManager.handleDespawn(payload));
        });

        ClientPlayNetworking.registerGlobalReceiver(EntityOverlayS2C.TYPE, (payload, context) -> {
            context.client().execute(() ->
                EntityOverlayStore.handle(payload));
        });

        ClientPlayNetworking.registerGlobalReceiver(
            ChestOverlayS2C.TYPE, (payload, context) -> {
                context.client().execute(() ->
                    ChestOverlayStore.handle(payload));
            });

        ClientPlayNetworking.registerGlobalReceiver(
            KeybindRebindS2C.TYPE, (payload, context) -> {
                context.client().execute(() ->
                    KeybindManager.handleRebindRequest(payload.slot()));
            });
        ClientPlayNetworking.registerGlobalReceiver(KeybindDeclarationsS2C.TYPE, (payload, context) -> {
            context.client().execute(() ->
                KeybindManager.handleDeclarations(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(
            justfatlard.pandorical.protocol.ActionMenusS2C.TYPE, (payload, context) -> {
                context.client().execute(() ->
                    justfatlard.pandorical.client.actions.ActionMenus.offered(payload.menus()));
            });
        ClientPlayNetworking.registerGlobalReceiver(KeybindDefaultsS2C.TYPE, (payload, context) -> {
            context.client().execute(() ->
                KeybindManager.applyDefaults(payload));
        });

        ServerSettingsButton.register();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            // There is nobody to tell when the file is read at startup, so it is said here.
            justfatlard.pandorical.client.actions.ActionMenus.sayIfDamaged();
            if (ContentManager.wasConfigPhaseSynced()) {
                // Synchronously, before any chunk is decoded.
                Pandorical.LOGGER.info("Play phase joined — remapping block state IDs synchronously");
                ContentManager.remapBlockStateIds();
                // Normally the configuration phase has already reloaded, before the level exists.
                if (!ContentManager.wasConfigReloadDone()) {
                    client.execute(ContentManager::injectResourcePack);
                }
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(
            justfatlard.pandorical.protocol.AddToMenuS2C.TYPE, (payload, context) -> {
                context.client().execute(() ->
                    justfatlard.pandorical.client.actions.ActionMenus.addToMenu(payload.command()));
            });

        ClientPlayNetworking.registerGlobalReceiver(
            justfatlard.pandorical.protocol.ClientModToggleS2C.TYPE, (payload, context) -> {
                context.client().execute(() ->
                    justfatlard.pandorical.client.settings.ClientModFiles.ask(payload.file()));
            });

        ClientConfigurationConnectionEvents.INIT.register((handler, client) -> forgetConnection());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> forgetConnection());
    }

    /**
     * Also run as a connection starts: one that fails in the configuration phase never fires the
     * play disconnect, and its leftovers would stall the next join.
     */
    private static void forgetConnection() {
        // Filled on the network thread during configuration, so cleared here, before the next
        // server's packets can arrive. Everything else is render-thread state.
        ContentManager.reset();
        ClientNotices.forgetConnection();
        justfatlard.pandorical.client.settings.ClientModFiles.forget();
        ClientInventorySlotRegistry.reset();
        Minecraft.getInstance().execute(PandoricalClient::forgetRenderState);
    }

    private static void forgetRenderState() {
        // What the last server promoted is no use on the next one: its commands may not exist.
        justfatlard.pandorical.client.actions.ActionMenus.forgetPromoted();
        pendingContainerDefs.clear();
        CameraManager.onDisconnect();
        HudManager.clear();
        ClientEntityRendererRegistry.reset();
        StructureManager.clear();
        EntityOverlayStore.clear();
        ChestOverlayStore.clear();
        KeybindManager.clear();
        VanillaHudElementSuppressor.clear();
        ServerCapabilities.clear();
        ViewportReporter.clear();
        ClientBlockMarks.clear();
        // Server state, and no use on the next one: left behind, it showed through as another
        // server's buttons, decals and painted blocks.
        ClientInventoryButtons.clear();
        BannerDecalStore.clear();
        PositionalTintStore.clear();
        LeafCulling.onDisconnect();
        EntityAnimations.clearAll();
        MountPolicy.clear();
        SkinOverrides.clearAll();
    }
}
