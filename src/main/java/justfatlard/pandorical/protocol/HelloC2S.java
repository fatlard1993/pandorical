package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

public record HelloC2S(
    int protocolVersion,
    List<String> capabilities
) implements CustomPacketPayload {
    public static final Type<HelloC2S> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "hello_c2s"));

    public static final StreamCodec<ByteBuf, HelloC2S> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, HelloC2S::protocolVersion,
        ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(32)), HelloC2S::capabilities,
        HelloC2S::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
