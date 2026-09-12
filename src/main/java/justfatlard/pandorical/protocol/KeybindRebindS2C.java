package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Binds the next key pressed to this slot; a negative slot cancels. */
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
