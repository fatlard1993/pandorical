package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The answer to {@link AssetsOfferConfigS2C}: the login waits for it either way. */
public record AssetsWantConfigC2S(boolean want) implements CustomPacketPayload {
    public static final Type<AssetsWantConfigC2S> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "assets_want_config"));

    public static final StreamCodec<ByteBuf, AssetsWantConfigC2S> STREAM_CODEC =
        ByteBufCodecs.BOOL.map(AssetsWantConfigC2S::new, AssetsWantConfigC2S::want);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
