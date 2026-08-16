package justfatlard.pandorical.api;

/**
 * Ids of the vanilla HUD elements a server mod can ask Pandorical clients to stop
 * drawing, via {@link HudApi#hideVanillaElements}. They mirror Fabric's
 * {@code VanillaHudElements} identifiers; the client resolves them by id, so an id
 * a given client build does not know is ignored rather than fatal.
 */
public final class VanillaHudElement {
    private VanillaHudElement() {}

    public static final String MISC_OVERLAYS = "minecraft:misc_overlays";
    public static final String CROSSHAIR = "minecraft:crosshair";
    public static final String SPECTATOR_MENU = "minecraft:spectator_menu";
    public static final String HOTBAR = "minecraft:hotbar";
    public static final String ARMOR_BAR = "minecraft:armor_bar";
    public static final String HEALTH_BAR = "minecraft:health_bar";
    public static final String FOOD_BAR = "minecraft:food_bar";
    public static final String AIR_BAR = "minecraft:air_bar";
    public static final String MOUNT_HEALTH = "minecraft:mount_health";
    public static final String INFO_BAR = "minecraft:info_bar";
    public static final String EXPERIENCE_LEVEL = "minecraft:experience_level";
    public static final String HELD_ITEM_TOOLTIP = "minecraft:held_item_tooltip";
    public static final String SPECTATOR_TOOLTIP = "minecraft:spectator_tooltip";
    public static final String MOB_EFFECTS = "minecraft:mob_effects";
    public static final String BOSS_BAR = "minecraft:boss_bar";
    public static final String SLEEP = "minecraft:sleep";
    public static final String DEMO_TIMER = "minecraft:demo_timer";
    public static final String SCOREBOARD = "minecraft:scoreboard";
    public static final String OVERLAY_MESSAGE = "minecraft:overlay_message";
    public static final String TITLE_AND_SUBTITLE = "minecraft:title_and_subtitle";
    public static final String CHAT = "minecraft:chat";
    public static final String PLAYER_LIST = "minecraft:player_list";
    public static final String SUBTITLES = "minecraft:subtitles";
}
