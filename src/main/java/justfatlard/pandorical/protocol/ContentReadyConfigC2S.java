package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Sent once the client has registered the synced content and loaded its assets. */
public record ContentReadyConfigC2S() implements CustomPacketPayload {
    public static final Type<ContentReadyConfigC2S> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "content_ready_config"));

    public static final StreamCodec<ByteBuf, ContentReadyConfigC2S> STREAM_CODEC =
        StreamCodec.unit(new ContentReadyConfigC2S());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
