package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * What the mods screen shows about each of a client's own mods beyond its name. A channel of its
 * own beside {@link ClientSettingsC2S}, whose layout is frozen.
 */
public record ClientModAboutC2S(List<About> mods) implements CustomPacketPayload {
    public static final Type<ClientModAboutC2S> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "client_mod_about"));

    public record Link(String label, String url) {
        static final StreamCodec<ByteBuf, Link> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), Link::label,
            ByteBufCodecs.stringUtf8(512), Link::url,
            Link::new);
    }

    /** {@code bundled} is the mods packed inside this one, each as its name and version, comma-separated. */
    public record About(String id, String license, String contributors, String icon, List<Link> links, String bundled) {
        static final StreamCodec<ByteBuf, About> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), About::id,
            ByteBufCodecs.stringUtf8(256), About::license,
            ByteBufCodecs.stringUtf8(1024), About::contributors,
            ByteBufCodecs.stringUtf8(256), About::icon,
            Link.STREAM_CODEC.apply(ByteBufCodecs.list(16)), About::links,
            ByteBufCodecs.stringUtf8(2048), About::bundled,
            About::new);
    }

    public static final StreamCodec<ByteBuf, ClientModAboutC2S> STREAM_CODEC =
        About.STREAM_CODEC.apply(ByteBufCodecs.list(512)).map(ClientModAboutC2S::new, ClientModAboutC2S::mods);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
