package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Asks the client to bind the next key pressed to this pooled slot; a negative slot cancels.
 *
 * <p>The rebinding itself has to happen on the client, because the binding is a line in that
 * player's options file and nothing else. The server only says which slot the player asked to
 * change, and hears the answer back as {@link KeybindBindingsC2S}.
 */
public record KeybindRebindS2C(
    int slot
) implements CustomPacketPayload {
    public static final Type<KeybindRebindS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "keybind_rebind"));

    public static final StreamCodec<ByteBuf, KeybindRebindS2C> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, KeybindRebindS2C::slot,
        KeybindRebindS2C::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
