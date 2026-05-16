package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

public record HelloS2C(
    int protocolVersion,
    List<String> capabilities
) implements CustomPacketPayload {
    public static final Type<HelloS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "hello_s2c"));

    public static final StreamCodec<ByteBuf, HelloS2C> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, HelloS2C::protocolVersion,
        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), HelloS2C::capabilities,
        HelloS2C::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
