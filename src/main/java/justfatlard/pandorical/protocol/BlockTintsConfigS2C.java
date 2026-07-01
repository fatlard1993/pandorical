package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Configuration-phase packet delivering block tint registrations to the client.
 * Each entry maps a tint type to the block IDs that should use it.
 */
public record BlockTintsConfigS2C(List<Entry> entries) implements CustomPacketPayload {
    public static final Type<BlockTintsConfigS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "block_tints_config"));

    public record Entry(String tintType, int constantColor, List<String> blockIds) {
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public Entry decode(ByteBuf buf) {
                String tintType = ByteBufCodecs.STRING_UTF8.decode(buf);
                int constantColor = ByteBufCodecs.VAR_INT.decode(buf);
                List<String> blockIds = ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).decode(buf);
                return new Entry(tintType, constantColor, blockIds);
            }
            @Override
            public void encode(ByteBuf buf, Entry value) {
                ByteBufCodecs.STRING_UTF8.encode(buf, value.tintType());
                ByteBufCodecs.VAR_INT.encode(buf, value.constantColor());
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).encode(buf, value.blockIds());
            }
        };
    }

    public static final StreamCodec<ByteBuf, BlockTintsConfigS2C> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BlockTintsConfigS2C decode(ByteBuf buf) {
            return new BlockTintsConfigS2C(Entry.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf));
        }
        @Override
        public void encode(ByteBuf buf, BlockTintsConfigS2C value) {
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, value.entries());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
