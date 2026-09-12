package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Each slot's key in pool order, named as the client's controls screen names it. The binding lives
 * only in the client's options file.
 */
public record KeybindBindingsC2S(
    List<String> keys
) implements CustomPacketPayload {
    public static final Type<KeybindBindingsC2S> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "keybind_bindings"));

    public static final StreamCodec<ByteBuf, KeybindBindingsC2S> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(16)), KeybindBindingsC2S::keys,
        KeybindBindingsC2S::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
