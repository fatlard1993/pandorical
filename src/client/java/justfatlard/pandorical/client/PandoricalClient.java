package justfatlard.pandorical.client;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.client.camera.CameraManager;
import justfatlard.pandorical.client.component.ComponentRegistry;
import justfatlard.pandorical.client.content.ContentManager;
import justfatlard.pandorical.client.hud.HudManager;
import justfatlard.pandorical.client.hud.HudRenderer;
import justfatlard.pandorical.client.inventory.ClientInventorySlotRegistry;
import justfatlard.pandorical.client.renderer.ClientEntityRendererRegistry;
import justfatlard.pandorical.client.screen.PandoricalContainerScreen;
import justfatlard.pandorical.client.screen.PandoricalScreen;
import justfatlard.pandorical.client.structure.StructureManager;
import justfatlard.pandorical.client.structure.StructureRenderer;
import justfatlard.pandorical.protocol.*;
import justfatlard.pandorical.screen.PandoricalMenu;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Block;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PandoricalClient implements ClientModInitializer {
    private static final List<String> CLIENT_CAPABILITIES = List.of("screens", "content", "hud", "camera", "structures", "entity_overlays", "chest_overlays", "keybinds", "hud_elements", "skins", "render_policy", "animations", "mount_policy");

    // Pending screen defs keyed by screenId; LinkedHashMap preserves insertion order
    // so the last entry is always the most recently added.
    // Accessed only on the render thread (via client.execute), so no ConcurrentHashMap needed.
    private static final Map<String, OpenScreenS2C> pendingContainerDefs = new LinkedHashMap<>();

    /**
     * Startup pieces left out by {@code -Dpandorical.skip=a,b,...}: keybinds, contextmodels,
     * suppressor, hud, structures, decals, or all. For finding which one a crash lives in on a
     * machine nobody here can reach; nothing is left out without the property.
     */
    private static final java.util.Set<String> SKIP = java.util.Arrays.stream(
            System.getProperty("pandorical.skip", "").split(","))
        .map(String::trim).filter(s -> !s.isEmpty()).collect(java.util.stream.Collectors.toSet());

    private static boolean skipped(String piece) {
        justfatlard.pandorical.Diagnostics.mark("startup: " + piece);
        boolean skip = SKIP.contains(piece) || SKIP.contains("all");
        if (skip) Pandorical.LOGGER.warn("[pandorical] diagnostic: leaving out {}", piece);
        return skip;
    }

    @Override
    public void onInitializeClient() {
        justfatlard.pandorical.client.diag.StackSampler.start();
        justfatlard.pandorical.Diagnostics.mark("client init begins");
        // The load guard (see Diagnostics): up from launch already, and raised again for every join,
        // from the first packet of the configuration phase - where the synced pack loads - until
        // the player has been in the world a while.
        net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents.INIT.register((handler, client) -> {
            justfatlard.pandorical.Diagnostics.guardFor(justfatlard.pandorical.Diagnostics.JOIN_WINDOW_MILLIS);
            justfatlard.pandorical.client.diag.StackSampler.start();
        });
        if (justfatlard.pandorical.Diagnostics.windowsClient()) {
            justfatlard.pandorical.client.api.PandoricalClientApi.settings().group(Pandorical.MOD_ID, "Pandorical")
                .toggle("loadGuard", "Load guard",
                    "Loads a little slower on Windows, to dodge a crash some Windows players get while loading",
                    justfatlard.pandorical.Diagnostics::guarding, justfatlard.pandorical.Diagnostics::setGuarding);
        }
        // The block-shape hooks in common code ask about marks; this is the client's answer.
        justfatlard.pandorical.BlockMarkLookup.client = justfatlard.pandorical.client.renderer.ClientBlockMarks::has;
        justfatlard.pandorical.client.settings.ContainerHabits.register();
        ComponentRegistry.registerDefaults();

        // The menu is built by vanilla's MenuType factory, which is handed nothing but a sync
        // id and an inventory - so the slot count has to be fetched from the definition that
        // arrived just before it. See PandoricalMenu's client constructor for what a wrong
        // count does to the player's inventory.
        justfatlard.pandorical.screen.PandoricalMenu.setIncomingModSlots(() -> {
            OpenScreenS2C newest = null;
            for (var entry : pendingContainerDefs.entrySet()) newest = entry.getValue();
            return newest == null ? -1 : newest.container().map(c -> c.slotCount()).orElse(-1);
        });

        // Keybind pool must register during client init: the options system
        // does not accept KeyMappings added later (see KeybindApi javadoc)
        if (!skipped("keybinds")) justfatlard.pandorical.client.keybind.KeybindManager.init();
        if (!skipped("contextmodels")) {
            justfatlard.pandorical.client.rail.ContextModels.register(new justfatlard.pandorical.client.rail.RailDiagonals());
            justfatlard.pandorical.client.rail.ContextModels.register(new justfatlard.pandorical.client.rail.FenceGateJoins());
            justfatlard.pandorical.client.rail.ContextModels.register(new justfatlard.pandorical.client.rail.DoorBanks());
            justfatlard.pandorical.client.rail.ContextModels.register(new justfatlard.pandorical.client.rail.TrapdoorBanks());
            justfatlard.pandorical.client.rail.ContextModels.register(new justfatlard.pandorical.client.rail.DoorJambs());
            justfatlard.pandorical.client.rail.ContextModels.register(new justfatlard.pandorical.client.rail.SlabHung());
            justfatlard.pandorical.client.rail.ContextModels.init();
        }

        // Same startup-time constraint as keybinds: Fabric's HUD element registry
        // is only writable during client init (see the suppressor's javadoc)
        if (!skipped("suppressor")) justfatlard.pandorical.client.hud.VanillaHudElementSuppressor.init();

        // Exact count for oversized stacks (whose slot label is abbreviated
        // by ItemCountRendererMixin), absorbed from stackz's client
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            int count = stack.getCount();
            if (count >= 100) {
                lines.add(net.minecraft.network.chat.Component.literal(
                        "Count: " + java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(count))
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        });

        MenuScreens.register(Pandorical.MENU_TYPE, PandoricalClient::createContainerScreen);

        registerConfigPhaseReceivers();
        registerClientHandlers();

        if (!skipped("hud")) HudRenderer.register();
        if (!skipped("structures")) StructureRenderer.register();
        if (!skipped("decals")) justfatlard.pandorical.client.decal.BannerDecalRenderer.register();

        // Tick content manager for sync timeout detection + show sync overlay
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ContentManager.tick();
            StructureManager.tick();
            HudManager.tick();
            justfatlard.pandorical.client.keybind.KeybindManager.tick(client);
            justfatlard.pandorical.client.settings.ViewportReporter.tick(client);
            if (ContentManager.isSyncing() && client.gui != null) {
                // Show as both title and actionbar for visibility
                client.gui.hud.setTitle(net.minecraft.network.chat.Component.literal(ContentManager.getSyncStatus())
                    .withStyle(net.minecraft.ChatFormatting.GOLD));
                client.gui.hud.setTimes(0, 40, 10);
            }
        });

        Pandorical.LOGGER.info("Pandorical client initialized");
    }

    /**
     * Factory for creating PandoricalContainerScreen from a PandoricalMenu.
     * Takes the most recently added pending def (insertion-ordered via LinkedHashMap).
     */
    private static PandoricalContainerScreen createContainerScreen(
            PandoricalMenu menu, Inventory inventory, Component title) {
        OpenScreenS2C screenDef = null;
        String foundKey = null;

        // LinkedHashMap iteration is insertion-ordered; last entry is newest
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
     * Register config-phase receivers for content sync.
     * These run BEFORE Fabric's registry sync, on the network thread.
     * The client registers blocks/items here so Fabric's sync sees them.
     */
    private void registerConfigPhaseReceivers() {
        // Receive content definitions during config phase
        ClientConfigurationNetworking.registerGlobalReceiver(SyncContentConfigS2C.TYPE, (payload, context) -> {
            Pandorical.LOGGER.info("Config phase: received content sync — {} blocks, {} items, {} expected asset chunks",
                payload.blocks().size(), payload.items().size(), payload.expectedAssetChunks());
            ContentManager.handleConfigSyncContent(payload);
        });

        // Receive asset chunks during config phase
        ClientConfigurationNetworking.registerGlobalReceiver(SyncAssetsConfigS2C.TYPE, (payload, context) -> {
            Pandorical.LOGGER.debug("Config phase: received asset chunk {}/{}",
                payload.chunkIndex() + 1, payload.totalChunks());
            ContentManager.handleConfigSyncAssets(payload);
        });

        // Receive extra inventory slot registrations during config phase so that
        // ClientInventorySlotRegistry is populated BEFORE InventoryMenu is constructed
        // on play-phase entry (InventoryMenu.<init> fires before any play packets arrive).
        ClientConfigurationNetworking.registerGlobalReceiver(PlayerInventoryRegistrationsS2C.TYPE, (payload, context) -> {
            Pandorical.LOGGER.debug("Config phase: received {} extra inventory slot group(s)", payload.groups().size());
            ClientInventorySlotRegistry.receive(payload);
        });

        ClientConfigurationNetworking.registerGlobalReceiver(
            justfatlard.pandorical.protocol.InventoryButtonsS2C.TYPE, (payload, context) -> {
                justfatlard.pandorical.client.inventory.ClientInventoryButtons.set(payload.buttons());
                Pandorical.LOGGER.debug("Inventory buttons received: {}", payload.buttons().size());
            });

        // The same list again, mid-game, when a button that is a switch has been thrown. The
        // screen reads the list every frame, so an open inventory shows the new face at once.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            justfatlard.pandorical.protocol.InventoryButtonsS2C.TYPE, (payload, context) ->
                justfatlard.pandorical.client.inventory.ClientInventoryButtons.set(payload.buttons()));

        // The server refuses any client it cannot send this to, on the grounds that a client
        // too old to receive it is too old to read the content that follows. That test only
        // measures anything if a receiver exists: Fabric advertises a channel to the server
        // only when something is listening on it, so for as long as this was missing the check
        // turned away every client, current ones included.
        ClientConfigurationNetworking.registerGlobalReceiver(
            justfatlard.pandorical.protocol.RequirementS2C.TYPE, (payload, context) -> {
                if (Pandorical.PROTOCOL_VERSION < payload.minimumProtocol()) {
                    Pandorical.LOGGER.warn("Server needs Pandorical {} (protocol v{}); this client speaks v{}",
                        payload.serverModVersion(), payload.minimumProtocol(), Pandorical.PROTOCOL_VERSION);
                } else {
                    Pandorical.LOGGER.debug("Server runs Pandorical {} — protocol v{}, minimum v{}",
                        payload.serverModVersion(), payload.protocolVersion(), payload.minimumProtocol());
                }
            });

        ClientConfigurationNetworking.registerGlobalReceiver(BlockTintsConfigS2C.TYPE, (payload, context) -> {
            Pandorical.LOGGER.debug("Config phase: received {} block tint group(s)", payload.entries().size());
            // A fresh connection starts unpainted; the server states every colour again on join.
            justfatlard.pandorical.client.renderer.PositionalTintStore.clear();
            justfatlard.pandorical.client.decal.BannerDecalStore.clear();
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
            case "positional"-> justfatlard.pandorical.client.renderer.PositionalTintStore.source(entry.constantColor());
            default -> {
                Pandorical.LOGGER.warn("Unknown block tint type '{}' — skipping", entry.tintType());
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
            justfatlard.pandorical.client.renderer.PositionalTintStore.track(blocks);
        }
    }

    private void registerClientHandlers() {
        // Respond to server hello
        ClientPlayNetworking.registerGlobalReceiver(
            justfatlard.pandorical.protocol.BlockMarksS2C.TYPE, (payload, context) ->
                context.client().execute(() -> justfatlard.pandorical.client.renderer.ClientBlockMarks.apply(payload)));
        ClientPlayNetworking.registerGlobalReceiver(
            justfatlard.pandorical.protocol.BlockTintPositionsS2C.TYPE, (payload, context) ->
                context.client().execute(() -> payload.entries().forEach(entry ->
                    justfatlard.pandorical.client.renderer.PositionalTintStore.paint(
                        entry.pos(), entry.argb()))));
        ClientPlayNetworking.registerGlobalReceiver(
            justfatlard.pandorical.protocol.ClientSettingS2C.TYPE, (payload, context) ->
                context.client().execute(() -> justfatlard.pandorical.client.settings.ClientSettings.INSTANCE.apply(payload)));
        ClientPlayNetworking.registerGlobalReceiver(
            justfatlard.pandorical.protocol.BannerDecalsS2C.TYPE, (payload, context) ->
                context.client().execute(() -> justfatlard.pandorical.client.decal.BannerDecalStore.apply(payload)));

        justfatlard.pandorical.client.keepsake.ClientKeepsakes.register();

        ClientPlayNetworking.registerGlobalReceiver(HelloS2C.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (payload.protocolVersion() != Pandorical.PROTOCOL_VERSION) {
                    Pandorical.LOGGER.warn("Server has Pandorical protocol v{} (client is v{}) — features may not work correctly",
                        payload.protocolVersion(), Pandorical.PROTOCOL_VERSION);
                }
                Pandorical.LOGGER.debug("Server hello received, protocol v{}, capabilities: {}",
                    payload.protocolVersion(), payload.capabilities());
                justfatlard.pandorical.client.settings.ServerCapabilities.set(payload.capabilities());
                ClientPlayNetworking.send(new HelloC2S(Pandorical.PROTOCOL_VERSION, CLIENT_CAPABILITIES));
                // And what this client's own mods want in the menu, now that there is a server to tell.
                justfatlard.pandorical.client.settings.ClientSettings.INSTANCE.send();
                justfatlard.pandorical.client.settings.ViewportReporter.send(context.client());
            });
        });

        // Open screen
        ClientPlayNetworking.registerGlobalReceiver(OpenScreenS2C.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (payload.container().isPresent()) {
                    // Store by screenId; the vanilla menu open arrives next
                    pendingContainerDefs.put(payload.screenId(), payload);
                } else {
                    PandoricalScreen screen = new PandoricalScreen(payload);
                    Minecraft.getInstance().gui.setScreen(screen);
                }
            });
        });

        // Update screen
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

        // Close screen
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

        // HUD handlers
        ClientPlayNetworking.registerGlobalReceiver(ShowHudS2C.TYPE, (payload, context) -> {
            context.client().execute(() -> HudManager.handleShow(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(UpdateHudS2C.TYPE, (payload, context) -> {
            context.client().execute(() -> HudManager.handleUpdate(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(HideHudS2C.TYPE, (payload, context) -> {
            context.client().execute(() -> HudManager.handleHide(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(justfatlard.pandorical.protocol.SetVanillaHudElementsS2C.TYPE, (payload, context) -> {
            context.client().execute(() ->
                justfatlard.pandorical.client.hud.VanillaHudElementSuppressor.handle(payload));
        });

        // Content sync
        ClientPlayNetworking.registerGlobalReceiver(SyncContentS2C.TYPE, (payload, context) -> {
            context.client().execute(() -> ContentManager.handleSyncContent(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(SyncAssetsS2C.TYPE, (payload, context) -> {
            context.client().execute(() -> ContentManager.handleSyncAssets(payload));
        });

        // Camera hints
        ClientPlayNetworking.registerGlobalReceiver(CameraHintS2C.TYPE, (payload, context) -> {
            context.client().execute(() -> CameraManager.handleHint(payload));
        });

        // Animations are ordinary client resources, so they arrive through the same asset sync as
        // the models they move and reload with them.
        net.fabricmc.fabric.api.resource.ResourceManagerHelper
            .get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
            .registerReloadListener(new justfatlard.pandorical.client.animation.AnimationLibrary());

        ClientPlayNetworking.registerGlobalReceiver(
            justfatlard.pandorical.protocol.MountPolicyS2C.TYPE, (payload, context) -> {
                context.client().execute(() -> justfatlard.pandorical.MountPolicy.set(
                    payload.doubleRiders(), payload.freeLook()));
            });

        ClientPlayNetworking.registerGlobalReceiver(
            justfatlard.pandorical.protocol.PlayAnimationS2C.TYPE, (payload, context) -> {
                context.client().execute(() -> {
                    if (payload.animation().isEmpty()) {
                        justfatlard.pandorical.client.animation.EntityAnimations.stop(payload.entityId());
                    } else {
                        justfatlard.pandorical.client.animation.EntityAnimations.play(
                            payload.entityId(),
                            net.minecraft.resources.Identifier.parse(payload.animation()),
                            payload.looping());
                    }
                });
            });

        ClientPlayNetworking.registerGlobalReceiver(
            justfatlard.pandorical.protocol.RenderPolicyS2C.TYPE, (payload, context) -> {
                context.client().execute(() ->
                    justfatlard.pandorical.client.render.LeafCulling.setEnforced(payload.cullLeaves()));
            });

        ClientPlayNetworking.registerGlobalReceiver(
            justfatlard.pandorical.protocol.SkinOverrideS2C.TYPE, (payload, context) -> {
                context.client().execute(() ->
                    justfatlard.pandorical.client.skin.SkinOverrides.handle(payload));
            });

        // Entity renderer registrations: apply to EntityRenderers.PROVIDERS
        ClientPlayNetworking.registerGlobalReceiver(EntityRenderersS2C.TYPE, (payload, context) -> {
            context.client().execute(() -> ClientEntityRendererRegistry.applyRenderers(payload));
        });

        // Structure handlers
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
        ClientPlayNetworking.registerGlobalReceiver(DespawnStructureS2C.TYPE, (payload, context) -> {
            context.client().execute(() -> StructureManager.handleDespawn(payload));
        });

        // Entity overlays
        ClientPlayNetworking.registerGlobalReceiver(EntityOverlayS2C.TYPE, (payload, context) -> {
            context.client().execute(() ->
                justfatlard.pandorical.client.renderer.EntityOverlayStore.handle(payload));
        });

        // Chest overlays
        ClientPlayNetworking.registerGlobalReceiver(
            justfatlard.pandorical.protocol.ChestOverlayS2C.TYPE, (payload, context) -> {
                context.client().execute(() ->
                    justfatlard.pandorical.client.renderer.ChestOverlayStore.handle(payload));
            });

        // Keybind slot declarations
        ClientPlayNetworking.registerGlobalReceiver(
            justfatlard.pandorical.protocol.KeybindRebindS2C.TYPE, (payload, context) -> {
                context.client().execute(() ->
                    justfatlard.pandorical.client.keybind.KeybindManager.handleRebindRequest(payload.slot()));
            });
        ClientPlayNetworking.registerGlobalReceiver(KeybindDeclarationsS2C.TYPE, (payload, context) -> {
            context.client().execute(() ->
                justfatlard.pandorical.client.keybind.KeybindManager.handleDeclarations(payload));
        });

        // When entering play phase, inject resource pack if config-phase synced assets
        justfatlard.pandorical.client.settings.ServerSettingsButton.register();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            justfatlard.pandorical.client.settings.ServerCapabilities.clear();
            justfatlard.pandorical.client.settings.ViewportReporter.clear();
            justfatlard.pandorical.client.renderer.ClientBlockMarks.clear();
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (ContentManager.wasConfigPhaseSynced()) {
                // Remap SYNCHRONOUSLY before any chunks are decoded
                Pandorical.LOGGER.info("Play phase joined — remapping block state IDs synchronously");
                ContentManager.remapBlockStateIds();
                // The configuration phase reloads before the level exists; only a client that
                // somehow reached play without that still reloads here, on top of the level.
                if (!ContentManager.wasConfigReloadDone()) {
                    client.execute(() -> ContentManager.injectResourcePackAndReRender(client));
                }
            }
        });

        // Reset at the START of every new connection, not just on the previous one's
        // DISCONNECT: a failure during the config->play handshake itself (e.g. Fabric's
        // own registry-sync rejecting an unknown block) can end a connection without ever
        // firing ClientPlayConnectionEvents.DISCONNECT, since that event is play-phase-only
        // and this kind of failure happens before JOIN. Relying solely on cleanup-after-
        // disconnect left ContentManager's static state (configPhaseSynced in particular)
        // stuck from the failed attempt, silently no-op'ing every asset chunk on the next
        // connection attempt and leaving the client stuck on "joining" with no error at all.
        ClientConfigurationConnectionEvents.INIT.register((handler, client) -> {
            pendingContainerDefs.clear();
            ContentManager.reset();
            CameraManager.onDisconnect();
            HudManager.clear();
            ClientInventorySlotRegistry.reset();
            ClientEntityRendererRegistry.reset();
            StructureManager.clear();
            justfatlard.pandorical.client.renderer.EntityOverlayStore.clear();
            justfatlard.pandorical.client.renderer.ChestOverlayStore.clear();
            justfatlard.pandorical.client.keybind.KeybindManager.clear();
            // Skins are textures, and a texture is released on the render thread only; this
            // hook fires on the network thread, and the renderer refused every release with a
            // wrong-thread warning per skin.
            net.minecraft.client.Minecraft.getInstance().execute(
                justfatlard.pandorical.client.skin.SkinOverrides::clearAll);
            justfatlard.pandorical.client.render.LeafCulling.onDisconnect();
            justfatlard.pandorical.client.animation.EntityAnimations.clearAll();
            justfatlard.pandorical.MountPolicy.clear();
            justfatlard.pandorical.client.hud.VanillaHudElementSuppressor.clear();
        });

        // Disconnect cleanup
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            pendingContainerDefs.clear();
            ContentManager.reset();
            CameraManager.onDisconnect();
            HudManager.clear();
            ClientInventorySlotRegistry.reset();
            ClientEntityRendererRegistry.reset();
            StructureManager.clear();
            justfatlard.pandorical.client.renderer.EntityOverlayStore.clear();
            justfatlard.pandorical.client.renderer.ChestOverlayStore.clear();
            justfatlard.pandorical.client.keybind.KeybindManager.clear();
            justfatlard.pandorical.client.hud.VanillaHudElementSuppressor.clear();
        });
    }
}
