package justfatlard.pandorical.api;

import justfatlard.pandorical.config.Keepsakes;
import justfatlard.pandorical.content.ContentRegistry;
import justfatlard.pandorical.hud.HudRegistry;
import justfatlard.pandorical.keybind.KeybindPool;
import justfatlard.pandorical.picture.PictureRegistry;
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
import justfatlard.pandorical.settings.SettingsRegistry;
import justfatlard.pandorical.structure.StructureRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Start here. Each accessor returns one feature's API: {@link #screens()}, {@link #hud()},
 * {@link #structures()} and the rest below.
 *
 * <p>Guard a send with {@link #isAvailable(ServerPlayer)}, or with
 * {@link #hasCapability(ServerPlayer, String)} and one of {@link Capabilities} for a single feature;
 * a vanilla client has neither.
 *
 * <p>Do per-player setup in {@link #onPlayerReady}, not Fabric's JOIN: JOIN fires before the
 * handshake, so every capability-gated call made there sends nothing.
 */
public final class PandoricalApi {
    private PandoricalApi() {}

    private static final ScreenRegistry SCREENS = ScreenRegistry.INSTANCE;
    private static final HudRegistry HUD = HudRegistry.INSTANCE;
    private static final ContentRegistry CONTENT = new ContentRegistry();
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

    // --- Per-player session state ---
    private static final Map<UUID, Set<String>> playerCapabilities = new ConcurrentHashMap<>();
    private static final Set<UUID> contentReadyPlayers = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> contentSyncStarted = ConcurrentHashMap.newKeySet();
    private static final List<Consumer<ServerPlayer>> playerReadyListeners = new CopyOnWriteArrayList<>();

    private static final BannerDecals BANNER_DECALS = BannerDecals.INSTANCE;
    private static final BlockMarks BLOCK_MARKS = BlockMarks.INSTANCE;
    private static final SettingsRegistry SETTINGS = new SettingsRegistry();

    // --- Framework ---

    /**
     * Returns true when Pandorical is loaded on the server. Always true: without Pandorical this
     * class is not there to ask, so declare {@code "pandorical"} in {@code fabric.mod.json}'s depends.
     */
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
    public static void onPlayerReady(Consumer<ServerPlayer> listener) {
        playerReadyListeners.add(listener);
    }

    /** Returns the content API for registering custom blocks, items, and assets. */
    public static ContentApi content() { return CONTENT; }

    /**
     * Register an entity type to be rendered with the given renderer key on Pandorical clients.
     * Supported keys: {@code "thrown_item"}, {@code "invisible"}.
     * Must be called during server-side mod initialisation.
     *
     * @param entityType  the entity type (must already be registered in the vanilla registry)
     * @param rendererKey a renderer key string
     */
    public static void registerEntityRenderer(EntityType<?> entityType, String rendererKey) {
        EntityRendererRegistry.register(entityType, rendererKey);
    }

    // --- Screens and HUD ---

    /** Returns the screen API for opening, updating, and closing declarative screens. */
    public static ScreenApi screens() { return SCREENS; }

    /**
     * Returns the screen ID of the screen currently open for this player via Pandorical,
     * or null if no Pandorical screen is open. Useful for mods that need to push updates
     * to a screen they opened earlier without tracking the ID themselves.
     */
    public static String getOpenScreenId(UUID playerUuid) {
        return SCREENS.openScreenId(playerUuid);
    }

    /** Returns the HUD API for showing, updating, and hiding HUD overlays. */
    public static HudApi hud() { return HUD; }

    // --- World ---

    /** Returns the structure API for showing moving, rotating block clusters as one batch-rendered object. */
    public static StructureApi structures() { return STRUCTURES; }

    /** Nether portals that go back the way they came. See {@link PortalApi}. */
    public static PortalApi portals() { return PORTALS; }

    /** Returns the picture API for pictures anchored to entities, painted and seen changing. */
    public static PictureApi pictures() { return PictureRegistry.INSTANCE; }

    /** Returns the animation API for playing synced animations on entities. */
    public static AnimationApi animations() { return ANIMATIONS; }

    /** Returns the entity overlay API for drawing an extra texture layer over a living entity's model. */
    public static EntityOverlayApi entityOverlays() { return ENTITY_OVERLAYS; }

    /** Returns the block tint API for registering biome-color and constant tint mappings. */
    public static BlockTintApi blockTints() { return BLOCK_TINTS; }

    /** Returns the block mark API for words on block positions that every client can read. */
    public static BlockMarkApi blockMarks() { return BLOCK_MARKS; }

    /** Returns the banner decal API for banner patterns laid flat on blocks, per player. */
    public static BannerDecalApi bannerDecals() { return BANNER_DECALS; }

    /** Returns the chest overlay API for drawing particular chests with another texture, per player. */
    public static ChestOverlayApi chestOverlays() { return CHEST_OVERLAYS; }

    /** Returns the render API for server-wide rendering policies, such as culled leaves. */
    public static RenderApi render() { return RENDER; }

    // --- Players ---

    /** Returns the camera API for adjusting camera distance and perspective for a player. */
    public static CameraApi camera() { return CAMERA; }

    /** Returns the skin API for deciding what skin a player is seen wearing. */
    public static SkinApi skins() { return SKINS; }

    /** Returns the mount API for server-wide riding rules: double riders and free look. */
    public static MountApi mounts() { return MOUNTS; }

    /** Returns the player inventory API for extra inventory slots that persist across sessions. */
    public static PlayerInventoryApi playerInventory() { return PLAYER_INVENTORY; }

    /** Returns the keybind API for rebindable key presses from Pandorical clients. */
    public static KeybindApi keybinds() { return KEYBINDS; }

    /** Per-player settings, shown to the player on one screen instead of behind commands. */
    public static SettingsApi settings() { return SETTINGS; }

    /** Values left with a player's own game, and handed back when they join. */
    public static KeepsakeApi keepsakes() { return Keepsakes.INSTANCE; }

    // --- Internal methods (used by Pandorical core, not for consuming mods) ---

    /** @hidden fired by the Hello handshake receiver once capabilities are registered */
    public static void firePlayerReady(ServerPlayer player) {
        for (var listener : playerReadyListeners) listener.accept(player);
    }

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
    public static ContentRegistry contentRegistry() { return CONTENT; }

    /** @hidden */
    public static ScreenRegistry screensImpl() { return SCREENS; }

    /** @hidden used by InventoryMenuMixin */
    public static PlayerInventoryApiImpl playerInventoryImpl() { return PLAYER_INVENTORY; }

    /** @hidden */
    public static BlockTints blockTintsImpl() { return BLOCK_TINTS; }

    /** @hidden used by Pandorical's server-stop, player-ready and level-change hooks */
    public static BlockMarks blockMarksImpl() { return BLOCK_MARKS; }

    /** @hidden used by Pandorical's EntityTrackingEvents registration */
    public static StructureRegistry structuresImpl() { return STRUCTURES; }

    /** @hidden used by Pandorical's EntityTrackingEvents/ServerEntityEvents registration */
    public static EntityOverlays entityOverlaysImpl() { return ENTITY_OVERLAYS; }

    /** @hidden used by Pandorical's KeyPressC2S receiver and handshake push */
    public static KeybindPool keybindsImpl() { return KEYBINDS; }

    /** @hidden used by Pandorical's init and disconnect hook, SettingsCommand and ClientMods */
    public static SettingsRegistry settingsImpl() { return SETTINGS; }
}
