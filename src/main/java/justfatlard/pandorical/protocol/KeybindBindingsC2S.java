package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * What each pooled keybind slot is bound to on this client, in the order of the pool, as the
 * client's own controls screen would name it. The server has no other way to know: the binding
 * lives in the player's options file and never leaves the client otherwise.
 *
 * <p>Sent when the server declares its slots and again after any rebind, so the mods menu shows
 * the key that is actually bound rather than the one a mod asked for.
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
