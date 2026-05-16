package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record CloseScreenS2C(
    String screenId
) implements CustomPacketPayload {
    public static final Type<CloseScreenS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "close_screen"));

    public static final StreamCodec<ByteBuf, CloseScreenS2C> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, CloseScreenS2C::screenId,
        CloseScreenS2C::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
