package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * One slice of a captured PNG. Sent in slices because vanilla refuses a serverbound payload over
 * 32 KiB, and a picture is larger than that.
 *
 * @param total how many slices the picture is in; 0, with no bytes, for a capture the client declined
 */
public record CapturePartC2S(int id, int index, int total, byte[] bytes) implements CustomPacketPayload {
    /** Room under vanilla's 32767 for the header. */
    public static final int MOST_BYTES = 32_000;

    public static CapturePartC2S declined(int id) {
        return new CapturePartC2S(id, 0, 0, new byte[0]);
    }

    public static final Type<CapturePartC2S> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "capture_part"));

    public static final StreamCodec<ByteBuf, CapturePartC2S> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, CapturePartC2S::id,
        ByteBufCodecs.VAR_INT, CapturePartC2S::index,
        ByteBufCodecs.VAR_INT, CapturePartC2S::total,
        ByteBufCodecs.byteArray(MOST_BYTES), CapturePartC2S::bytes,
        CapturePartC2S::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
