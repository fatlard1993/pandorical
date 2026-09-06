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

/**
 * The mods a player's own client brought, kept per player so the menu can list them.
 *
 * <p>A client-side mod is on nobody's server, so what the menu knows of it is what the client
 * said: name, version, description, readme and settings, with the values the client holds. The
 * values live on the client. The menu edits them the way it edits any setting, and every change
 * is handed straight back to the client to apply and keep; the copy here is only what the
 * screen shows, refreshed whenever the client says its values moved.
 */
public final class ClientMods {
    private ClientMods() {}

    private static final Map<UUID, List<ClientSettingsC2S.Mod>> byPlayer = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, String>> values = new ConcurrentHashMap<>();

    public static void declare(ServerPlayer player, ClientSettingsC2S payload) {
        byPlayer.put(player.getUUID(), payload.mods());
        Map<String, String> mine = values.computeIfAbsent(player.getUUID(), id -> new ConcurrentHashMap<>());
        SettingsRegistry registry = PandoricalApi.settingsImpl();
        for (ClientSettingsC2S.Mod mod : payload.mods()) {
            for (ClientSettingsC2S.Setting setting : mod.settings()) {
                mine.put(mod.id() + ":" + setting.key(), setting.value());
                if (registry.find(mod.id(), setting.key()) == null) register(registry, mod, setting);
            }
        }
    }

    public static void forget(ServerPlayer player) {
        byPlayer.remove(player.getUUID());
        values.remove(player.getUUID());
    }

    /** The player's client mods as catalogue entries, or nothing for a client that declared none. */
    public static List<ModCatalog.ModInfo> of(ServerPlayer player) {
        List<ModCatalog.ModInfo> out = new ArrayList<>();
        for (ClientSettingsC2S.Mod mod : byPlayer.getOrDefault(player.getUUID(), List.of())) {
            out.add(new ModCatalog.ModInfo(mod.id(), mod.name(), mod.version() + "  (this client)",
                mod.authors(), mod.description(), mod.readme()));
        }
        return out;
    }

    /**
     * A client setting as a registry setting, backed by the per-player copy and the packet that
     * carries a change back. Registered once per key, on the first client that declares it; the
     * schema is the mod's, not the player's.
     */
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
