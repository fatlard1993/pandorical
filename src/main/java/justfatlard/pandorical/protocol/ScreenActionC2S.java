package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Universal client-to-server UI interaction. Replaces per-mod payload types.
 * The server routes by screenType + componentId.
 */
public record ScreenActionC2S(
    String screenId,
    String componentId,
    String action,
    Map<String, String> data
) implements CustomPacketPayload {
    public static final Type<ScreenActionC2S> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "screen_action"));

    private static final StreamCodec<ByteBuf, String> SHORT_STRING = ByteBufCodecs.stringUtf8(256);

    public static final StreamCodec<ByteBuf, ScreenActionC2S> STREAM_CODEC = StreamCodec.composite(
        SHORT_STRING, ScreenActionC2S::screenId,
        SHORT_STRING, ScreenActionC2S::componentId,
        SHORT_STRING, ScreenActionC2S::action,
        ByteBufCodecs.map(HashMap::new, ByteBufCodecs.stringUtf8(1024), ByteBufCodecs.stringUtf8(1024)), ScreenActionC2S::data,
        ScreenActionC2S::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
