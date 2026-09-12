package justfatlard.pandorical.settings;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ServerSettings extends SavedData {
    private static final String STORAGE_KEY = "pandorical_server_settings";

    private record Entry(String key, String value) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("key").forGetter(Entry::key),
            Codec.STRING.fieldOf("value").forGetter(Entry::value)
        ).apply(instance, Entry::new));
    }

    public static final Codec<ServerSettings> CODEC = Entry.CODEC.listOf()
        .xmap(ServerSettings::fromEntries, ServerSettings::toEntries);

    private static final SavedDataType<ServerSettings> TYPE = new SavedDataType<>(
        Identifier.parse(STORAGE_KEY), ServerSettings::new, CODEC, DataFixTypes.LEVEL);

    private final Map<String, String> values = new HashMap<>();

    public static ServerSettings get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public String get(String key) {
        return this.values.get(key);
    }

    public void put(String key, String value) {
        this.values.put(key, value);
        this.setDirty();
    }

    private static ServerSettings fromEntries(List<Entry> entries) {
        ServerSettings settings = new ServerSettings();
        for (Entry entry : entries) settings.values.put(entry.key(), entry.value());
        return settings;
    }

    private static List<Entry> toEntries(ServerSettings settings) {
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<String, String> e : settings.values.entrySet()) entries.add(new Entry(e.getKey(), e.getValue()));
        return entries;
    }
}
