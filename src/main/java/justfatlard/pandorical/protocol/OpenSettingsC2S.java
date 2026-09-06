package justfatlard.pandorical.protocol;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import io.netty.buffer.ByteBuf;

/** A client asking for the settings screen: the options menu's button, pressed. */
public record OpenSettingsC2S() implements CustomPacketPayload {

    public static final Type<OpenSettingsC2S> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "open_settings"));

    public static final StreamCodec<ByteBuf, OpenSettingsC2S> STREAM_CODEC = StreamCodec.unit(new OpenSettingsC2S());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
