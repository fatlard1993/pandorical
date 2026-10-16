package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Take a {@code side} square picture of the world as seen from a point, drawn off screen, and send
 * it back as {@link CapturePartC2S}s carrying {@code id}. A payload of its own, not a field on
 * {@link CaptureRequestS2C}, so a server can tell which clients can take one.
 */
public record CaptureViewRequestS2C(int id, int side, double x, double y, double z, float yRot, float xRot)
    implements CustomPacketPayload {

    public static final Type<CaptureViewRequestS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "capture_view_request"));

    public static final StreamCodec<ByteBuf, CaptureViewRequestS2C> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, CaptureViewRequestS2C::id,
        ByteBufCodecs.VAR_INT, CaptureViewRequestS2C::side,
        ByteBufCodecs.DOUBLE, CaptureViewRequestS2C::x,
        ByteBufCodecs.DOUBLE, CaptureViewRequestS2C::y,
        ByteBufCodecs.DOUBLE, CaptureViewRequestS2C::z,
        ByteBufCodecs.FLOAT, CaptureViewRequestS2C::yRot,
        ByteBufCodecs.FLOAT, CaptureViewRequestS2C::xRot,
        CaptureViewRequestS2C::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
