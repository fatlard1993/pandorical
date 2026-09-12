package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * The client binds each slot once, if still unbound, and remembers that by keybind id, so a
 * player who clears it keeps it clear. A separate payload, so an older client is not sent it.
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
