package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * The client forwards presses only for these slots. Their names arrive as lang entries in the
 * synced asset pack, not here.
 */
public record KeybindDeclarationsS2C(
    List<Integer> claimedSlots
) implements CustomPacketPayload {

    public static final Type<KeybindDeclarationsS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "keybind_declarations"));

    public static final StreamCodec<ByteBuf, KeybindDeclarationsS2C> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(16)), KeybindDeclarationsS2C::claimedSlots,
        KeybindDeclarationsS2C::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
