package justfatlard.pandorical.client.settings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.client.api.ClientSettingsApi;
import justfatlard.pandorical.protocol.ClientSettingS2C;
import justfatlard.pandorical.protocol.ClientSettingsC2S;
import justfatlard.pandorical.settings.ModCatalog;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** The client half: what client mods declared, sent to whichever server the player is on. */
public final class ClientSettings implements ClientSettingsApi {
    public static final ClientSettings INSTANCE = new ClientSettings();

    private final List<GroupImpl> groups = new ArrayList<>();

    private ClientSettings() {}

    @Override
    public Group group(String modId, String modName) {
        for (GroupImpl group : groups) {
            if (group.modId.equals(modId)) return group;
        }
        GroupImpl group = new GroupImpl(modId, modName);
        groups.add(group);
        return group;
    }

    @Override
    public void changed() {
        send();
    }

    /** Everything declared, with current values, to the server. Nothing to say, nothing sent. */
    public void send() {
        if (groups.isEmpty() || !ClientPlayNetworking.canSend(ClientSettingsC2S.TYPE)) return;
        List<ClientSettingsC2S.Mod> mods = new ArrayList<>();
        for (GroupImpl group : groups) mods.add(group.describe());
        ClientPlayNetworking.send(new ClientSettingsC2S(mods));
    }

    /** The server relays a change made in the menu; the mod's own setter keeps it. */
    public void apply(ClientSettingS2C payload) {
        for (GroupImpl group : groups) {
            if (!group.modId.equals(payload.modId())) continue;
            SettingImpl setting = group.settings.get(payload.key());
            if (setting != null) setting.accept().accept(payload.value());
        }
    }

    private record SettingImpl(ClientSettingsC2S.Setting shape, Supplier<String> get, Consumer<String> accept) {}

    private static final class GroupImpl implements Group {
        final String modId;
        final String modName;
        final Map<String, SettingImpl> settings = new LinkedHashMap<>();

        GroupImpl(String modId, String modName) {
            this.modId = modId;
            this.modName = modName;
        }

        @Override
        public Group toggle(String key, String label, String description, Supplier<Boolean> get, Consumer<Boolean> set) {
            settings.put(key, new SettingImpl(
                new ClientSettingsC2S.Setting(key, label, orEmpty(description), "toggle", Map.of(), 0, 0, 0, ""),
                () -> String.valueOf(get.get()), v -> set.accept(Boolean.parseBoolean(v))));
            return this;
        }

        @Override
        public Group choice(String key, String label, String description, Map<String, String> options,
                Supplier<String> get, Consumer<String> set) {
            settings.put(key, new SettingImpl(
                new ClientSettingsC2S.Setting(key, label, orEmpty(description), "choice", new LinkedHashMap<>(options), 0, 0, 0, ""),
                get, v -> { if (options.containsKey(v)) set.accept(v); }));
            return this;
        }

        @Override
        public Group number(String key, String label, String description, int min, int max, int step,
                Supplier<Integer> get, Consumer<Integer> set) {
            settings.put(key, new SettingImpl(
                new ClientSettingsC2S.Setting(key, label, orEmpty(description), "number", Map.of(), min, max, step, ""),
                () -> String.valueOf(get.get()),
                v -> { try { set.accept(Math.clamp(Integer.parseInt(v), min, max)); } catch (NumberFormatException ignored) { } }));
            return this;
        }

        ClientSettingsC2S.Mod describe() {
            ModCatalog.ModInfo info = ModCatalog.find(modId);
            List<ModCatalog.Line> readme = info != null ? info.readme() : List.of();
            List<ClientSettingsC2S.Setting> shaped = new ArrayList<>();
            for (SettingImpl setting : settings.values()) {
                ClientSettingsC2S.Setting shape = setting.shape();
                shaped.add(new ClientSettingsC2S.Setting(shape.key(), shape.label(), shape.description(), shape.kind(),
                    shape.options(), shape.min(), shape.max(), shape.step(), setting.get().get()));
            }
            return new ClientSettingsC2S.Mod(modId, info != null ? info.name() : modName,
                info != null ? info.version() : "", info != null ? info.authors() : "",
                info != null ? info.description() : "", readme, shaped);
        }

        private static String orEmpty(String s) {
            return s == null ? "" : s;
        }
    }

    static {
        Pandorical.LOGGER.debug("Client settings registry ready");
    }
}
