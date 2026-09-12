package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The keys this server's keybinds would like to be on, for the ones that asked to be bound by
 * default. The client binds each slot once, if it is still unbound, and remembers having done so
 * by the keybind's id, so a player who clears it afterwards keeps it clear.
 *
 * <p>Its own payload rather than a field on {@link KeybindDeclarationsS2C}: a client from before
 * it simply never registers the type, the server sees it cannot send it, and nothing about the
 * declarations every client already reads has changed shape.
 */
public record KeybindDefaultsS2C(List<Entry> entries) implements CustomPacketPayload {

    public record Entry(int slot, String id, int key) {
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Entry::slot,
            ByteBufCodecs.stringUtf8(256), Entry::id,
            ByteBufCodecs.VAR_INT, Entry::key,
            Entry::new
        );
    }

    public static final Type<KeybindDefaultsS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "keybind_defaults"));

    public static final StreamCodec<ByteBuf, KeybindDefaultsS2C> STREAM_CODEC = StreamCodec.composite(
        Entry.STREAM_CODEC.apply(ByteBufCodecs.list(16)), KeybindDefaultsS2C::entries,
        KeybindDefaultsS2C::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
