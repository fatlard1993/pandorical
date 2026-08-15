package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * A press of one of the client's pooled Pandorical keybinds. Carries only the
 * slot index; all meaning (which mod, which action) lives server-side in the
 * slot registration map, so a malicious or stale client can at worst press a
 * key the server ignores. The server validates the slot range, that the slot
 * is claimed, that the sender asserted the {@code "keybinds"} capability, and
 * rate-limits dispatches per player per tick.
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
