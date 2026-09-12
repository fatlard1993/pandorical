package justfatlard.pandorical.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.SettingsApi;
import justfatlard.pandorical.protocol.ClientSettingS2C;
import justfatlard.pandorical.protocol.ClientSettingsC2S;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * The mods a player's own client declared. Their values live on the client: every change is sent
 * back to it, and the copy here only feeds the screen.
 */
public final class ClientMods {
    private ClientMods() {}

    private static final Map<UUID, List<ClientSettingsC2S.Mod>> byPlayer = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, String>> values = new ConcurrentHashMap<>();

    /** A registered setting lasts the server's life, so what clients may add to it is capped. */
    private static final int MOST_MODS = 64;
    private static final int MOST_SETTINGS_PER_MOD = 64;
    private static final int MOST_REGISTERED = 1024;
    private static final Pattern MOD_ID = Pattern.compile("[a-z][a-z0-9_-]{1,63}");
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_.-]{1,64}");
    private static final AtomicInteger registered = new AtomicInteger();

    public static void declare(ServerPlayer player, ClientSettingsC2S payload) {
        List<ClientSettingsC2S.Mod> mods = payload.mods().stream()
            .filter(mod -> MOD_ID.matcher(mod.id()).matches())
            .limit(MOST_MODS)
            .toList();
        byPlayer.put(player.getUUID(), mods);
        Map<String, String> mine = values.computeIfAbsent(player.getUUID(), id -> new ConcurrentHashMap<>());
        SettingsRegistry registry = PandoricalApi.settingsImpl();
        for (ClientSettingsC2S.Mod mod : mods) {
            int settings = 0;
            for (ClientSettingsC2S.Setting setting : mod.settings()) {
                if (!KEY.matcher(setting.key()).matches()) continue;
                if (++settings > MOST_SETTINGS_PER_MOD) break;
                mine.put(mod.id() + ":" + setting.key(), setting.value());
                if (registry.find(mod.id(), setting.key()) == null) {
                    if (registered.incrementAndGet() > MOST_REGISTERED) {
                        registered.decrementAndGet();
                        continue;
                    }
                    register(registry, mod, setting);
                }
            }
        }
    }

    public static void forget(ServerPlayer player) {
        byPlayer.remove(player.getUUID());
        values.remove(player.getUUID());
    }

    public static List<ModCatalog.ModInfo> of(ServerPlayer player) {
        List<ModCatalog.ModInfo> out = new ArrayList<>();
        for (ClientSettingsC2S.Mod mod : byPlayer.getOrDefault(player.getUUID(), List.of())) {
            out.add(new ModCatalog.ModInfo(mod.id(), mod.name(), mod.version() + "  (this client)",
                mod.authors(), mod.description(), mod.readme()));
        }
        return out;
    }

    /** Once per key, from the first client that declares it: the schema is the mod's, not the player's. */
    private static void register(SettingsRegistry registry, ClientSettingsC2S.Mod mod, ClientSettingsC2S.Setting setting) {
        SettingsApi.Group group = registry.clientGroup(mod.id(), mod.name());
        String id = mod.id() + ":" + setting.key();
        switch (setting.kind()) {
            case "toggle" -> group.toggle(setting.key(), setting.label(), Boolean.parseBoolean(setting.value()))
                .describe(blank(setting.description()))
                .backedBy(p -> Boolean.parseBoolean(stored(p, id, setting.value())),
                    (p, v) -> push(p, mod.id(), setting.key(), id, String.valueOf(v)));
            case "number" -> group.number(setting.key(), setting.label(), setting.min(), setting.max(), setting.step(),
                    parse(setting.value(), setting.min()))
                .describe(blank(setting.description()))
                .backedBy(p -> parse(stored(p, id, setting.value()), setting.min()),
                    (p, v) -> push(p, mod.id(), setting.key(), id, String.valueOf(v)));
            default -> group.choice(setting.key(), setting.label(), setting.options(), setting.value())
                .describe(blank(setting.description()))
                .backedBy(p -> stored(p, id, setting.value()),
                    (p, v) -> push(p, mod.id(), setting.key(), id, v));
        }
    }

    private static String blank(String description) {
        return description == null || description.isEmpty() ? null : description;
    }

    private static int parse(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (NumberFormatException e) { return fallback; }
    }

    private static String stored(ServerPlayer player, String id, String fallback) {
        Map<String, String> mine = values.get(player.getUUID());
        return mine == null ? fallback : mine.getOrDefault(id, fallback);
    }

    private static void push(ServerPlayer player, String modId, String key, String id, String value) {
        values.computeIfAbsent(player.getUUID(), u -> new ConcurrentHashMap<>()).put(id, value);
        if (ServerPlayNetworking.canSend(player, ClientSettingS2C.TYPE)) {
            ServerPlayNetworking.send(player, new ClientSettingS2C(modId, key, value));
        }
    }
}
