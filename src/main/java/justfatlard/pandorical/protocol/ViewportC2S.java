package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** In scaled pixels; sent after the hello and again when the window settles at a new size. */
public record ViewportC2S(int width, int height) implements CustomPacketPayload {

    public static final Type<ViewportC2S> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "viewport"));

    public static final StreamCodec<ByteBuf, ViewportC2S> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, ViewportC2S::width,
        ByteBufCodecs.VAR_INT, ViewportC2S::height,
        ViewportC2S::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
