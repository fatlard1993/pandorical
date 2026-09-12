package justfatlard.pandorical.api;

import justfatlard.pandorical.hud.HudRegistry;
import justfatlard.pandorical.keybind.KeybindPool;
import justfatlard.pandorical.portal.PortalPairing;
import justfatlard.pandorical.push.BannerDecals;
import justfatlard.pandorical.push.BlockMarks;
import justfatlard.pandorical.push.BlockTints;
import justfatlard.pandorical.push.CameraHints;
import justfatlard.pandorical.push.ChestOverlays;
import justfatlard.pandorical.push.DeclaredMountPolicy;
import justfatlard.pandorical.push.DeclaredRenderPolicy;
import justfatlard.pandorical.push.EntityOverlays;
import justfatlard.pandorical.push.PlayingAnimations;
import justfatlard.pandorical.push.SkinOverrides;
import justfatlard.pandorical.screen.ScreenRegistry;
import justfatlard.pandorical.structure.StructureRegistry;
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

    private static final ScreenRegistry SCREENS = ScreenRegistry.INSTANCE;
    private static final HudRegistry HUD = HudRegistry.INSTANCE;
    private static final justfatlard.pandorical.content.ContentRegistry CONTENT = new justfatlard.pandorical.content.ContentRegistry();
    private static final CameraHints CAMERA = CameraHints.INSTANCE;
    private static final SkinOverrides SKINS = SkinOverrides.INSTANCE;
    private static final DeclaredRenderPolicy RENDER = DeclaredRenderPolicy.INSTANCE;
    private static final PlayingAnimations ANIMATIONS = PlayingAnimations.INSTANCE;
    private static final DeclaredMountPolicy MOUNTS = DeclaredMountPolicy.INSTANCE;
    private static final PortalPairing PORTALS = PortalPairing.INSTANCE;
    private static final PlayerInventoryApiImpl PLAYER_INVENTORY = new PlayerInventoryApiImpl();
    private static final BlockTints BLOCK_TINTS = BlockTints.INSTANCE;
    private static final StructureRegistry STRUCTURES = StructureRegistry.INSTANCE;
    private static final EntityOverlays ENTITY_OVERLAYS = EntityOverlays.INSTANCE;
    private static final ChestOverlays CHEST_OVERLAYS = ChestOverlays.INSTANCE;
    private static final KeybindPool KEYBINDS = KeybindPool.INSTANCE;

    // --- Per-player state ---
    private static final Map<UUID, Set<String>> playerCapabilities = new ConcurrentHashMap<>();
    private static final Set<UUID> contentReadyPlayers = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> contentSyncStarted = ConcurrentHashMap.newKeySet();

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
     * Returns true if the player's Pandorical client advertised the given capability, one of
     * {@link Capabilities#CLIENT}. Any other string is false for every player. A capability being
     * absent means the client version does not support that feature.
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

    /** Values left with a player's own game, and handed back when they join. */
    public static KeepsakeApi keepsakes() { return justfatlard.pandorical.config.Keepsakes.INSTANCE; }
    /** Returns the content API for registering custom blocks, items, and assets. */
    public static ContentApi content() { return CONTENT; }
    /** Returns the camera API for adjusting camera distance and perspective for a player. */
    public static CameraApi camera() { return CAMERA; }

    public static SkinApi skins() { return SKINS; }

    public static RenderApi render() { return RENDER; }

    public static AnimationApi animations() { return ANIMATIONS; }

    public static MountApi mounts() { return MOUNTS; }

    /** Nether portals that go back the way they came. See {@link PortalApi}. */
    public static PortalApi portals() { return PORTALS; }

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

    private static final BannerDecals BANNER_DECALS = BannerDecals.INSTANCE;

    public static BannerDecalApi bannerDecals() { return BANNER_DECALS; }

    public static PictureApi pictures() { return justfatlard.pandorical.picture.PictureRegistry.INSTANCE; }

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

    private static final BlockMarks BLOCK_MARKS = BlockMarks.INSTANCE;
    public static BlockMarkApi blockMarks() { return BLOCK_MARKS; }
    public static BlockMarks blockMarksImpl() { return BLOCK_MARKS; }

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
        return SCREENS.openScreenId(playerUuid);
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


    // --- Internal methods (used by Pandorical core, not for consuming mods) ---

    /** @hidden */
    public static justfatlard.pandorical.content.ContentRegistry contentRegistry() { return CONTENT; }

    /** @hidden used by InventoryMenuMixin */
    public static PlayerInventoryApiImpl playerInventoryImpl() { return PLAYER_INVENTORY; }

    /** @hidden */
    public static BlockTints blockTintsImpl() { return BLOCK_TINTS; }

    /** @hidden used by Pandorical's EntityTrackingEvents registration */
    public static StructureRegistry structuresImpl() { return STRUCTURES; }

    /** @hidden used by Pandorical's EntityTrackingEvents/ServerEntityEvents registration */
    public static EntityOverlays entityOverlaysImpl() { return ENTITY_OVERLAYS; }

    /** @hidden used by Pandorical's KeyPressC2S receiver and handshake push */
    public static KeybindPool keybindsImpl() { return KEYBINDS; }

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
        SCREENS.forgetPlayer(playerUuid);
        KEYBINDS.removePlayer(playerUuid);
        HUD.forgetPlayer(playerUuid);
        PLAYER_INVENTORY.forgetButtonGlyphs(playerUuid);
    }

    /** @hidden */
    public static ScreenRegistry screensImpl() { return SCREENS; }
}
