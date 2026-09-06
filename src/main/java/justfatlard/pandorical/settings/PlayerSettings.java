package justfatlard.pandorical.settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * What each player chose, for the settings whose mods do not keep the value themselves.
 *
 * <p>One store for the whole server, on the overworld, because a setting is the player's and not
 * the dimension's. Values are strings on disk; each setting knows how to read its own.
 */
public final class PlayerSettings extends SavedData {
    private static final String STORAGE_KEY = "pandorical_player_settings";

    private record Entry(String key, String value) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("key").forGetter(Entry::key),
            Codec.STRING.fieldOf("value").forGetter(Entry::value)
        ).apply(instance, Entry::new));
    }

    private record PlayerEntry(UUID player, List<Entry> values) {
        static final Codec<PlayerEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("player").forGetter(PlayerEntry::player),
            Entry.CODEC.listOf().fieldOf("values").forGetter(PlayerEntry::values)
        ).apply(instance, PlayerEntry::new));
    }

    public static final Codec<PlayerSettings> CODEC = PlayerEntry.CODEC.listOf()
        .xmap(PlayerSettings::fromEntries, PlayerSettings::toEntries);

    private static final SavedDataType<PlayerSettings> TYPE = new SavedDataType<>(
        Identifier.parse(STORAGE_KEY), PlayerSettings::new, CODEC, DataFixTypes.LEVEL);

    private final Map<UUID, Map<String, String>> values = new HashMap<>();

    public static PlayerSettings get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public String get(UUID player, String key) {
        Map<String, String> own = this.values.get(player);
        return own == null ? null : own.get(key);
    }

    public void put(UUID player, String key, String value) {
        this.values.computeIfAbsent(player, k -> new HashMap<>()).put(key, value);
        this.setDirty();
    }

    private static PlayerSettings fromEntries(List<PlayerEntry> entries) {
        PlayerSettings settings = new PlayerSettings();
        for (PlayerEntry entry : entries) {
            Map<String, String> own = new HashMap<>();
            for (Entry value : entry.values()) own.put(value.key(), value.value());
            settings.values.put(entry.player(), own);
        }
        return settings;
    }

    private static List<PlayerEntry> toEntries(PlayerSettings settings) {
        List<PlayerEntry> entries = new ArrayList<>();
        for (Map.Entry<UUID, Map<String, String>> e : settings.values.entrySet()) {
            List<Entry> values = new ArrayList<>();
            for (Map.Entry<String, String> v : e.getValue().entrySet()) values.add(new Entry(v.getKey(), v.getValue()));
            entries.add(new PlayerEntry(e.getKey(), values));
        }
        return entries;
    }
}
