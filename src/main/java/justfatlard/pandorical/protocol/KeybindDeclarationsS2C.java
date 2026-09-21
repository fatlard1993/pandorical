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
    /**
     * Read generously, sent conservatively: the codec accepts what a later server may send, while
     * this server holds to the 16 a client already in players' hands can read.
     */
    public KeybindDeclarationsS2C {
        claimedSlots = Wire.fit(claimedSlots, 16, "claimed keybind slots");
    }

    public static final Type<KeybindDeclarationsS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "keybind_declarations"));

    public static final StreamCodec<ByteBuf, KeybindDeclarationsS2C> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(256)), KeybindDeclarationsS2C::claimedSlots,
        KeybindDeclarationsS2C::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
