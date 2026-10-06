package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Take a {@code side} square picture of the view and send it back as {@link CapturePartC2S}s carrying {@code id}. */
public record CaptureRequestS2C(int id, int side) implements CustomPacketPayload {

    public static final Type<CaptureRequestS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "capture_request"));

    public static final StreamCodec<ByteBuf, CaptureRequestS2C> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, CaptureRequestS2C::id,
        ByteBufCodecs.VAR_INT, CaptureRequestS2C::side,
        CaptureRequestS2C::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
