package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * {@link BlockMarksS2C} with a word for the whole set: {@code replace} is every mark in the world
 * the player is now in, sent as they arrive in it, and the client drops everything it held first,
 * so a mark taken off while they were elsewhere does not come back with them. The old channel stays
 * for clients that have only it.
 */
public record BlockMarksV2S2C(Identifier dimension, boolean replace, List<BlockMarksS2C.Entry> entries) implements CustomPacketPayload {
    public static final Type<BlockMarksV2S2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "block_marks_v2"));

    public static final StreamCodec<ByteBuf, BlockMarksV2S2C> STREAM_CODEC = StreamCodec.composite(
        Identifier.STREAM_CODEC, BlockMarksV2S2C::dimension,
        ByteBufCodecs.BOOL, BlockMarksV2S2C::replace,
        BlockMarksS2C.Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), BlockMarksV2S2C::entries,
        BlockMarksV2S2C::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
