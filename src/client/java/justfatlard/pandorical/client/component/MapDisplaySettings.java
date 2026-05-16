package justfatlard.pandorical.client.component;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Client-side display settings for the map minimap.
 * Stored at: {minecraft config dir}/map-plus-plus-display.json
 */
public class MapDisplaySettings {
    private static final Logger LOGGER = LoggerFactory.getLogger("MapDisplaySettings");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "map-plus-plus-display.json";

    // Set of passive/other mob type IDs for filtering purposes
    private static final Set<String> PASSIVE_OR_OTHER_IDS = buildPassiveOrOtherSet();

    // Loaded settings instance
    private static MapDisplaySettings instance = null;
    private static boolean loaded = false;

    // --- POJO fields (serialised by Gson) ---
    private String corner = "top_right";
    private boolean showHostile = true;
    private boolean showPassiveOther = true;
    private Set<String> disabledMobTypes = new HashSet<>();
    private float zoomLevel = 1.0f;
    private boolean showCoords = true;

    // --- Static API ---

    public static void load() {
        Path path = getConfigPath();
        if (Files.exists(path)) {
            try {
                String json = Files.readString(path);
                instance = GSON.fromJson(json, MapDisplaySettings.class);
                if (instance == null) instance = new MapDisplaySettings();
                if (instance.disabledMobTypes == null) instance.disabledMobTypes = new HashSet<>();
            } catch (Exception e) {
                LOGGER.error("[map-plus-plus] Failed to load display settings, using defaults", e);
                instance = new MapDisplaySettings();
            }
        } else {
            instance = new MapDisplaySettings();
        }
        loaded = true;
    }

    public static void save() {
        if (instance == null) instance = new MapDisplaySettings();
        try {
            Path path = getConfigPath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(instance));
        } catch (IOException e) {
            LOGGER.error("[map-plus-plus] Failed to save display settings", e);
        }
    }

    /** Ensure settings are loaded (lazy init for use during rendering). */
    public static void ensureLoaded() {
        if (!loaded) load();
    }

    // --- Getters ---

    public static String getCorner() {
        ensureLoaded();
        return instance.corner;
    }

    public static boolean isShowHostile() {
        ensureLoaded();
        return instance.showHostile;
    }

    public static boolean isShowPassiveOther() {
        ensureLoaded();
        return instance.showPassiveOther;
    }

    public static Set<String> getDisabledMobTypes() {
        ensureLoaded();
        return instance.disabledMobTypes;
    }

    // --- Setters ---

    public static void setCorner(String corner) {
        ensureLoaded();
        instance.corner = corner;
    }

    public static void setShowHostile(boolean v) {
        ensureLoaded();
        instance.showHostile = v;
    }

    public static void setShowPassiveOther(boolean v) {
        ensureLoaded();
        instance.showPassiveOther = v;
    }

    public static void setMobTypeDisabled(String entityTypeId, boolean disabled) {
        ensureLoaded();
        if (disabled) {
            instance.disabledMobTypes.add(entityTypeId);
        } else {
            instance.disabledMobTypes.remove(entityTypeId);
        }
    }

    public static float getZoomLevel() {
        ensureLoaded();
        return instance.zoomLevel;
    }

    public static void setZoomLevel(float zoomLevel) {
        ensureLoaded();
        instance.zoomLevel = zoomLevel;
    }

    public static boolean isShowCoords() {
        ensureLoaded();
        return instance.showCoords;
    }

    public static void setShowCoords(boolean v) {
        ensureLoaded();
        instance.showCoords = v;
    }

    // --- Helpers ---

    /**
     * Returns true if the given entity type ID belongs to the passive-or-other category.
     * Used by MapComponent to decide which category filter applies.
     */
    public static boolean isPassiveOrOther(String entityTypeId) {
        return PASSIVE_OR_OTHER_IDS.contains(entityTypeId);
    }

    private static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    private static Set<String> buildPassiveOrOtherSet() {
        Set<String> set = new HashSet<>();
        // Passive animals
        for (String path : new String[]{
            "bat", "bee", "cat", "chicken", "cod", "cow", "donkey", "fox",
            "frog", "goat", "horse", "mooshroom", "mule", "ocelot", "parrot",
            "pig", "polar_bear", "pufferfish", "rabbit", "salmon", "sheep",
            "sniffer", "squid", "strider", "tadpole", "tropical_fish",
            "turtle", "wolf"
        }) {
            set.add("minecraft:" + path);
        }
        // Other (neutral/utility)
        for (String path : new String[]{
            "axolotl", "glow_squid", "iron_golem", "snow_golem", "allay",
            "armadillo", "breeze"
        }) {
            set.add("minecraft:" + path);
        }
        return set;
    }
}
