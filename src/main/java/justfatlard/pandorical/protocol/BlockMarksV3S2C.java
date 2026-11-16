package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * {@link BlockMarksV2S2C} one chunk at a time. {@code replace} is every mark in that chunk, sent as
 * the chunk itself is sent to the player, and the client drops what it held there first; without
 * it the entries are changes to the chunk, sent to the players who have it. The client forgets a
 * chunk's marks as the server tells it to forget the chunk, and all of them as it changes world, since the server
 * tells it again as the chunks come back. The older channels stay for clients that have only them.
 *
 * @param chunk the chunk's packed position
 */
public record BlockMarksV3S2C(Identifier dimension, long chunk, boolean replace, List<BlockMarksS2C.Entry> entries) implements CustomPacketPayload {
    public static final Type<BlockMarksV3S2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "block_marks_v3"));

    public static final StreamCodec<ByteBuf, BlockMarksV3S2C> STREAM_CODEC = StreamCodec.composite(
        Identifier.STREAM_CODEC, BlockMarksV3S2C::dimension,
        ByteBufCodecs.LONG, BlockMarksV3S2C::chunk,
        ByteBufCodecs.BOOL, BlockMarksV3S2C::replace,
        BlockMarksS2C.Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), BlockMarksV3S2C::entries,
        BlockMarksV3S2C::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
