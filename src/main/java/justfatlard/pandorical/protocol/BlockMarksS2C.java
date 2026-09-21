package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

public record BlockMarksS2C(Identifier dimension, List<Entry> entries) implements CustomPacketPayload {
    public static final Type<BlockMarksS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "block_marks"));

    public record Entry(long pos, String mark, boolean on) {
        /** A trimmed mark matches nothing on the client; an overlong one would kick them. */
        public Entry {
            mark = Wire.fit(mark, 64, "a block mark");
        }

        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, Entry::pos,
            ByteBufCodecs.stringUtf8(64), Entry::mark,
            ByteBufCodecs.BOOL, Entry::on,
            Entry::new);
    }

    public static final StreamCodec<ByteBuf, BlockMarksS2C> STREAM_CODEC = StreamCodec.composite(
        Identifier.STREAM_CODEC, BlockMarksS2C::dimension,
        Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), BlockMarksS2C::entries,
        BlockMarksS2C::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
