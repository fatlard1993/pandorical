package justfatlard.pandorical.api;

/**
 * Ids of the vanilla HUD elements a server mod can ask Pandorical clients to stop
 * drawing, via {@link HudApi#hideVanillaElements}. They mirror Fabric's
 * {@code VanillaHudElements} identifiers; the client resolves them by id, so an id
 * a given client build does not know is ignored rather than fatal.
 *
 * <p><b>Four of these are declared but never suppressed.</b> {@link #CHAT},
 * {@link #PLAYER_LIST}, {@link #SLEEP} and {@link #DEMO_TIMER} are refused by the
 * client on purpose, so a server cannot blind a player to the things they need in
 * order to leave or to speak. {@link #SPECTATOR_MENU} and {@link #SPECTATOR_TOOLTIP}
 * are refused for the same reason while spectating. Passing any of them is not an
 * error: the client logs a warning and keeps drawing the element, so a HUD overlay
 * meant to replace one needs a layout that survives it staying put. They are listed
 * here because the constant existing and the suppression working are different
 * questions, and only one of them is visible from your side.
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
