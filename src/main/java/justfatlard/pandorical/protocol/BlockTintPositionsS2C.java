package justfatlard.pandorical.protocol;

import java.util.List;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Deltas: only the positions in this packet change. */
public record BlockTintPositionsS2C(List<Entry> entries) implements CustomPacketPayload {
    public static final Type<BlockTintPositionsS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "block_tint_positions"));

    /** @param argb the colour, or 0 to clear this position */
    public record Entry(long pos, int argb) {
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public Entry decode(ByteBuf buf) {
                return new Entry(buf.readLong(), buf.readInt());
            }
            @Override
            public void encode(ByteBuf buf, Entry value) {
                buf.writeLong(value.pos());
                buf.writeInt(value.argb());
            }
        };
    }

    public static final StreamCodec<ByteBuf, BlockTintPositionsS2C> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BlockTintPositionsS2C decode(ByteBuf buf) {
            return new BlockTintPositionsS2C(Entry.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf));
        }
        @Override
        public void encode(ByteBuf buf, BlockTintPositionsS2C value) {
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, value.entries());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
