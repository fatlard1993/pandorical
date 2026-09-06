package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * One of the client's pooled keybinds came back up. The press is {@link KeyPressC2S}; this is
 * its other edge, sent only for a claimed slot, and validated server-side the same way.
 */
public record KeyReleaseC2S(
    int slot
) implements CustomPacketPayload {
    public static final Type<KeyReleaseC2S> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "key_release"));
    public static final StreamCodec<ByteBuf, KeyReleaseC2S> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, KeyReleaseC2S::slot,
        KeyReleaseC2S::new
    );
    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
