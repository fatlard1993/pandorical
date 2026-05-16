package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client signals that content registration and asset loading is complete.
 */
public record ContentReadyC2S() implements CustomPacketPayload {
    public static final Type<ContentReadyC2S> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "content_ready"));

    public static final StreamCodec<ByteBuf, ContentReadyC2S> STREAM_CODEC =
        StreamCodec.unit(new ContentReadyC2S());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
