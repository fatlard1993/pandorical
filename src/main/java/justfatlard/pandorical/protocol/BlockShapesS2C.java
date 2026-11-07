package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Blocks whose shape the server decides position by position: for each, the boxes it is made of,
 * in the block's own space, or none to forget it and go back to the block's own shape.
 *
 * <p>{@code replace} is the whole set for the world the player is now in, sent as they arrive in
 * it: everything the client held before is dropped first, so a shape cleared while they were
 * elsewhere does not survive their coming back.
 */
public record BlockShapesS2C(Identifier dimension, boolean replace, List<Entry> entries) implements CustomPacketPayload {
    public static final Type<BlockShapesS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "block_shapes"));

    public record Box(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        public static final StreamCodec<ByteBuf, Box> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, Box::minX,
            ByteBufCodecs.FLOAT, Box::minY,
            ByteBufCodecs.FLOAT, Box::minZ,
            ByteBufCodecs.FLOAT, Box::maxX,
            ByteBufCodecs.FLOAT, Box::maxY,
            ByteBufCodecs.FLOAT, Box::maxZ,
            Box::new);
    }

    public record Entry(long pos, List<Box> boxes) {
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, Entry::pos,
            Box.STREAM_CODEC.apply(ByteBufCodecs.list()), Entry::boxes,
            Entry::new);
    }

    public static final StreamCodec<ByteBuf, BlockShapesS2C> STREAM_CODEC = StreamCodec.composite(
        Identifier.STREAM_CODEC, BlockShapesS2C::dimension,
        ByteBufCodecs.BOOL, BlockShapesS2C::replace,
        Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), BlockShapesS2C::entries,
        BlockShapesS2C::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
