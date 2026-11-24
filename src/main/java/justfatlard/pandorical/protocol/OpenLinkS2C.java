package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * A link a player pressed on the mods screen. The client asks before opening it, with the game's
 * own confirmation, and opens nothing but http and https.
 */
public record OpenLinkS2C(String url) implements CustomPacketPayload {
    public static final Type<OpenLinkS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "open_link"));

    public static final StreamCodec<ByteBuf, OpenLinkS2C> STREAM_CODEC =
        ByteBufCodecs.stringUtf8(512).map(OpenLinkS2C::new, OpenLinkS2C::url);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
