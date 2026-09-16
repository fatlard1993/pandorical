package justfatlard.pandorical.api;

import justfatlard.pandorical.content.ContentRegistry;
import justfatlard.pandorical.drops.DropsPolicy;
import justfatlard.pandorical.hud.HudRegistry;
import justfatlard.pandorical.keybind.KeybindPool;
import justfatlard.pandorical.login.Keepsakes;
import justfatlard.pandorical.maprelief.MapReliefRegistry;
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
 * Start here: each accessor returns one feature's API.
 *
 * <p>Guard a send with {@link #isAvailable(ServerPlayer)}, or with
 * {@link #hasCapability(ServerPlayer, String)} for a single feature; a vanilla client has neither.
 * Do per-player setup in {@link #onPlayerReady}, not Fabric's JOIN.
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
    private static final DropsPolicy DROPS = DropsPolicy.INSTANCE;
    private static final PlayerInventoryApiImpl PLAYER_INVENTORY = new PlayerInventoryApiImpl();
    private static final BlockTints BLOCK_TINTS = BlockTints.INSTANCE;
    private static final StructureRegistry STRUCTURES = StructureRegistry.INSTANCE;
    private static final EntityOverlays ENTITY_OVERLAYS = EntityOverlays.INSTANCE;
    private static final ChestOverlays CHEST_OVERLAYS = ChestOverlays.INSTANCE;
    private static final KeybindPool KEYBINDS = KeybindPool.INSTANCE;

    private static final Map<UUID, Set<String>> playerCapabilities = new ConcurrentHashMap<>();
    private static final Set<UUID> contentReadyPlayers = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> contentSyncStarted = ConcurrentHashMap.newKeySet();
    private static final List<Consumer<ServerPlayer>> playerReadyListeners = new CopyOnWriteArrayList<>();

    private static final BannerDecals BANNER_DECALS = BannerDecals.INSTANCE;
    private static final BlockMarks BLOCK_MARKS = BlockMarks.INSTANCE;
    private static final SettingsRegistry SETTINGS = new SettingsRegistry();

    /**
     * Always true: without Pandorical this class is not there to ask, so declare
     * {@code "pandorical"} in {@code fabric.mod.json}'s depends.
     */
    public static boolean isAvailable() { return true; }

    /** Whether the player has completed the Pandorical handshake; false for vanilla clients. */
    public static boolean isAvailable(ServerPlayer player) {
        return playerCapabilities.containsKey(player.getUUID());
    }

    /**
     * Whether the player's client declared the capability. False for every player for a string
     * not in {@link Capabilities#CLIENT}, a misspelt one included.
     */
    public static boolean hasCapability(ServerPlayer player, String capability) {
        Set<String> caps = playerCapabilities.get(player.getUUID());
        return caps != null && caps.contains(capability);
    }

    /** Whether the client has loaded synced content, or has none to load. */
    public static boolean isContentReady(ServerPlayer player) {
        if (!isAvailable(player)) return false;
        if (!CONTENT.hasContent()) return true;
        return contentReadyPlayers.contains(player.getUUID());
    }

    /**
     * Runs once per session, when the player's handshake has registered their capabilities and
     * content sync is underway: the place to restate per-player state. Fabric's JOIN fires before
     * the handshake, so a capability-gated call there silently sends nothing.
     */
    public static void onPlayerReady(Consumer<ServerPlayer> listener) {
        playerReadyListeners.add(listener);
    }

    public static ContentApi content() { return CONTENT; }

    /**
     * Call during server-side mod initialisation, after the entity type is registered.
     *
     * @param rendererKey {@link EntityRendererRegistry#KEY_THROWN_ITEM} or
     *                    {@link EntityRendererRegistry#KEY_INVISIBLE}
     * @throws IllegalArgumentException for any other key
     */
    public static void registerEntityRenderer(EntityType<?> entityType, String rendererKey) {
        EntityRendererRegistry.register(entityType, rendererKey);
    }

    public static ScreenApi screens() { return SCREENS; }

    /** The id of the Pandorical screen open for this player, or null. */
    public static String getOpenScreenId(UUID playerUuid) {
        return SCREENS.openScreenId(playerUuid);
    }

    public static HudApi hud() { return HUD; }

    public static StructureApi structures() { return STRUCTURES; }

    public static PortalApi portals() { return PORTALS; }

    public static DropsApi drops() { return DROPS; }

    public static MapReliefApi mapReliefs() { return MapReliefRegistry.INSTANCE; }

    public static PictureApi pictures() { return PictureRegistry.INSTANCE; }

    public static AnimationApi animations() { return ANIMATIONS; }

    public static EntityOverlayApi entityOverlays() { return ENTITY_OVERLAYS; }

    public static BlockTintApi blockTints() { return BLOCK_TINTS; }

    public static BlockMarkApi blockMarks() { return BLOCK_MARKS; }

    public static BannerDecalApi bannerDecals() { return BANNER_DECALS; }

    public static ChestOverlayApi chestOverlays() { return CHEST_OVERLAYS; }

    public static RenderApi render() { return RENDER; }

    public static CameraApi camera() { return CAMERA; }

    public static SkinApi skins() { return SKINS; }

    public static MountApi mounts() { return MOUNTS; }

    public static PlayerInventoryApi playerInventory() { return PLAYER_INVENTORY; }

    public static KeybindApi keybinds() { return KEYBINDS; }

    public static SettingsApi settings() { return SETTINGS; }

    public static KeepsakeApi keepsakes() { return Keepsakes.INSTANCE; }

    // --- Internal methods (used by Pandorical core, not for consuming mods) ---

    /** @hidden */
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
     * True once per connection, so a client re-sending HelloC2S cannot trigger the content and
     * asset rebuild again.
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

    /** @hidden */
    public static PlayerInventoryApiImpl playerInventoryImpl() { return PLAYER_INVENTORY; }

    /** @hidden */
    public static BlockTints blockTintsImpl() { return BLOCK_TINTS; }

    /** @hidden */
    public static BlockMarks blockMarksImpl() { return BLOCK_MARKS; }

    /** @hidden */
    public static StructureRegistry structuresImpl() { return STRUCTURES; }

    /** @hidden */
    public static EntityOverlays entityOverlaysImpl() { return ENTITY_OVERLAYS; }

    /** @hidden */
    public static KeybindPool keybindsImpl() { return KEYBINDS; }

    /** @hidden */
    public static SettingsRegistry settingsImpl() { return SETTINGS; }
}
