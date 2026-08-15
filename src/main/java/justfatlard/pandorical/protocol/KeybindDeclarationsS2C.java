package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Tells the client which pooled keybind slots this server has claimed. The
 * client only forwards presses for claimed slots, so unclaimed pool keys are
 * inert and never generate network traffic. Display names are not carried
 * here: they arrive as lang entries in the synced pandorical asset pack, so
 * the controls screen shows the server's names through the ordinary
 * translation path.
 *
 * <p>Sent after the capability handshake completes (not on raw join), only to
 * clients that asserted {@code "keybinds"}.
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
