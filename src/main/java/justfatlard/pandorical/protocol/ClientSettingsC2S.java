package justfatlard.pandorical.protocol;

import java.util.List;
import java.util.Map;

import io.netty.buffer.ByteBuf;
import justfatlard.pandorical.settings.ModCatalog;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.LinkedHashMap;

/**
 * A client's own mods and their settings, for the mod menu.
 *
 * <p>A client-side mod has no server half to declare anything, so the client declares for it:
 * who the mod is and what it can be asked, with the current answers. Sent whole after the hello,
 * and again whenever a value changes on the client's side.
 */
public record ClientSettingsC2S(List<Mod> mods) implements CustomPacketPayload {
    public static final Type<ClientSettingsC2S> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "client_settings"));

    /** One setting: kind is toggle, choice or number; options carry a choice's ids and labels. */
    public record Setting(String key, String label, String description, String kind,
            Map<String, String> options, int min, int max, int step, String value) {
        static final StreamCodec<ByteBuf, Setting> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public Setting decode(ByteBuf buf) {
                String key = ByteBufCodecs.STRING_UTF8.decode(buf);
                String label = ByteBufCodecs.STRING_UTF8.decode(buf);
                String description = ByteBufCodecs.STRING_UTF8.decode(buf);
                String kind = ByteBufCodecs.STRING_UTF8.decode(buf);
                int n = ByteBufCodecs.VAR_INT.decode(buf);
                Map<String, String> options = new LinkedHashMap<>();
                for (int i = 0; i < n; i++) {
                    options.put(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf));
                }
                int min = ByteBufCodecs.VAR_INT.decode(buf);
                int max = ByteBufCodecs.VAR_INT.decode(buf);
                int step = ByteBufCodecs.VAR_INT.decode(buf);
                String value = ByteBufCodecs.STRING_UTF8.decode(buf);
                return new Setting(key, label, description, kind, options, min, max, step, value);
            }

            @Override
            public void encode(ByteBuf buf, Setting v) {
                ByteBufCodecs.STRING_UTF8.encode(buf, v.key());
                ByteBufCodecs.STRING_UTF8.encode(buf, v.label());
                ByteBufCodecs.STRING_UTF8.encode(buf, v.description());
                ByteBufCodecs.STRING_UTF8.encode(buf, v.kind());
                ByteBufCodecs.VAR_INT.encode(buf, v.options().size());
                for (var option : v.options().entrySet()) {
                    ByteBufCodecs.STRING_UTF8.encode(buf, option.getKey());
                    ByteBufCodecs.STRING_UTF8.encode(buf, option.getValue());
                }
                ByteBufCodecs.VAR_INT.encode(buf, v.min());
                ByteBufCodecs.VAR_INT.encode(buf, v.max());
                ByteBufCodecs.VAR_INT.encode(buf, v.step());
                ByteBufCodecs.STRING_UTF8.encode(buf, v.value());
            }
        };
    }

    /** A block of readme, as {@link ModCatalog.Line}: what it is, how deep, and its text. */
    static final StreamCodec<ByteBuf, ModCatalog.Line> LINE_CODEC = new StreamCodec<>() {
        @Override
        public ModCatalog.Line decode(ByteBuf buf) {
            int kind = ByteBufCodecs.VAR_INT.decode(buf);
            int level = ByteBufCodecs.VAR_INT.decode(buf);
            String text = ByteBufCodecs.STRING_UTF8.decode(buf);
            ModCatalog.Kind[] kinds = ModCatalog.Kind.values();
            return new ModCatalog.Line(kinds[Math.clamp(kind, 0, kinds.length - 1)], level, text);
        }

        @Override
        public void encode(ByteBuf buf, ModCatalog.Line v) {
            ByteBufCodecs.VAR_INT.encode(buf, v.kind().ordinal());
            ByteBufCodecs.VAR_INT.encode(buf, v.level());
            ByteBufCodecs.STRING_UTF8.encode(buf, v.text());
        }
    };

    /** One client mod: what the menu says about it, and its settings. */
    public record Mod(String id, String name, String version, String authors, String description,
            List<ModCatalog.Line> readme, List<Setting> settings) {
        static final StreamCodec<ByteBuf, Mod> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public Mod decode(ByteBuf buf) {
                String id = ByteBufCodecs.STRING_UTF8.decode(buf);
                String name = ByteBufCodecs.STRING_UTF8.decode(buf);
                String version = ByteBufCodecs.STRING_UTF8.decode(buf);
                String authors = ByteBufCodecs.STRING_UTF8.decode(buf);
                String description = ByteBufCodecs.STRING_UTF8.decode(buf);
                List<ModCatalog.Line> readme = LINE_CODEC.apply(ByteBufCodecs.list()).decode(buf);
                List<Setting> settings = Setting.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf);
                return new Mod(id, name, version, authors, description, readme, settings);
            }

            @Override
            public void encode(ByteBuf buf, Mod v) {
                ByteBufCodecs.STRING_UTF8.encode(buf, v.id());
                ByteBufCodecs.STRING_UTF8.encode(buf, v.name());
                ByteBufCodecs.STRING_UTF8.encode(buf, v.version());
                ByteBufCodecs.STRING_UTF8.encode(buf, v.authors());
                ByteBufCodecs.STRING_UTF8.encode(buf, v.description());
                LINE_CODEC.apply(ByteBufCodecs.list()).encode(buf, v.readme());
                Setting.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, v.settings());
            }
        };
    }

    public static final StreamCodec<ByteBuf, ClientSettingsC2S> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ClientSettingsC2S decode(ByteBuf buf) {
            return new ClientSettingsC2S(Mod.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf));
        }

        @Override
        public void encode(ByteBuf buf, ClientSettingsC2S value) {
            Mod.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, value.mods());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
