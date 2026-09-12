package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Only a slot index: the meaning lives server-side, so a hostile client can at worst press a key
 * the server ignores.
 */
public record KeyPressC2S(
    int slot
) implements CustomPacketPayload {

    public static final Type<KeyPressC2S> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "key_press"));

    public static final StreamCodec<ByteBuf, KeyPressC2S> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, KeyPressC2S::slot,
        KeyPressC2S::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
