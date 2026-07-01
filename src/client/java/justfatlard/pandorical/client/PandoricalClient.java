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
import justfatlard.pandorical.protocol.*;
import justfatlard.pandorical.screen.PandoricalMenu;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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
    private static final List<String> CLIENT_CAPABILITIES = List.of("screens", "content", "hud", "camera");

    // Pending screen defs keyed by screenId — LinkedHashMap preserves insertion order
    // so the last entry is always the most recently added.
    // Accessed only on the render thread (via client.execute), so no ConcurrentHashMap needed.
    private static final Map<String, OpenScreenS2C> pendingContainerDefs = new LinkedHashMap<>();

    @Override
    public void onInitializeClient() {
        ComponentRegistry.registerDefaults();

        MenuScreens.register(Pandorical.MENU_TYPE, PandoricalClient::createContainerScreen);

        registerConfigPhaseReceivers();
        registerClientHandlers();

        HudRenderer.register();

        // Tick content manager for sync timeout detection + show sync overlay
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ContentManager.tick();
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

        // LinkedHashMap iteration is insertion-ordered — last entry is newest
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

        ClientConfigurationNetworking.registerGlobalReceiver(BlockTintsConfigS2C.TYPE, (payload, context) -> {
            Pandorical.LOGGER.debug("Config phase: received {} block tint group(s)", payload.entries().size());
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
            default -> {
                Pandorical.LOGGER.warn("Unknown block tint type '{}' — skipping", entry.tintType());
                yield null;
            }
        };
        if (source == null) return;

        Block[] blocks = entry.blockIds().stream()
            .map(id -> BuiltInRegistries.BLOCK.getValue(Identifier.of(id)))
            .filter(Objects::nonNull)
            .toArray(Block[]::new);
        if (blocks.length > 0) BlockColorRegistry.register(List.of(source), blocks);
    }

    private void registerClientHandlers() {
        // Respond to server hello
        ClientPlayNetworking.registerGlobalReceiver(HelloS2C.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (payload.protocolVersion() != Pandorical.PROTOCOL_VERSION) {
                    Pandorical.LOGGER.warn("Server has Pandorical protocol v{} (client is v{}) — features may not work correctly",
                        payload.protocolVersion(), Pandorical.PROTOCOL_VERSION);
                }
                Pandorical.LOGGER.debug("Server hello received, protocol v{}, capabilities: {}",
                    payload.protocolVersion(), payload.capabilities());
                ClientPlayNetworking.send(new HelloC2S(Pandorical.PROTOCOL_VERSION, CLIENT_CAPABILITIES));
            });
        });

        // Open screen
        ClientPlayNetworking.registerGlobalReceiver(OpenScreenS2C.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (payload.container().isPresent()) {
                    // Store by screenId — vanilla menu open arrives next
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

        // Entity renderer registrations — apply to EntityRenderers.PROVIDERS
        ClientPlayNetworking.registerGlobalReceiver(EntityRenderersS2C.TYPE, (payload, context) -> {
            context.client().execute(() -> ClientEntityRendererRegistry.applyRenderers(payload));
        });

        // When entering play phase, inject resource pack if config-phase synced assets
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (ContentManager.wasConfigPhaseSynced()) {
                // Remap SYNCHRONOUSLY before any chunks are decoded
                Pandorical.LOGGER.info("Play phase joined — remapping block state IDs synchronously");
                ContentManager.remapBlockStateIds();
                // Resource pack injection and re-render can be async
                client.execute(() -> ContentManager.injectResourcePackAndReRender(client));
            }
        });

        // Disconnect cleanup
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            pendingContainerDefs.clear();
            ContentManager.reset();
            CameraManager.onDisconnect();
            HudManager.clear();
            ClientInventorySlotRegistry.reset();
            ClientEntityRendererRegistry.reset();
        });
    }
}
