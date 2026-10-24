package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The synced assets by their SHA-256, ahead of the chunks: a client that already holds them
 * answers {@link AssetsWantConfigC2S} with no, and the chunks never leave the server.
 */
public record AssetsOfferConfigS2C(String sha256, int totalChunks) implements CustomPacketPayload {
    public static final Type<AssetsOfferConfigS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "assets_offer_config"));

    public static final StreamCodec<ByteBuf, AssetsOfferConfigS2C> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.stringUtf8(64), AssetsOfferConfigS2C::sha256,
        ByteBufCodecs.VAR_INT, AssetsOfferConfigS2C::totalChunks,
        AssetsOfferConfigS2C::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
