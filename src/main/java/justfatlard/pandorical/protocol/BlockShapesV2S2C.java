package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * {@link BlockShapesS2C} one chunk at a time. {@code replace} is every shape in that chunk, sent as
 * the chunk itself is sent to the player, and the client drops what it held there first; without
 * it the entries are changes to the chunk, sent to the players who have it. The client forgets a
 * chunk's shapes as the server tells it to forget the chunk, and all of them as it changes world, since the server
 * tells it again as the chunks come back. The old channel stays for clients that have only it.
 *
 * @param chunk the chunk's packed position
 */
public record BlockShapesV2S2C(Identifier dimension, long chunk, boolean replace, List<BlockShapesS2C.Entry> entries) implements CustomPacketPayload {
    public static final Type<BlockShapesV2S2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "block_shapes_v2"));

    public static final StreamCodec<ByteBuf, BlockShapesV2S2C> STREAM_CODEC = StreamCodec.composite(
        Identifier.STREAM_CODEC, BlockShapesV2S2C::dimension,
        ByteBufCodecs.LONG, BlockShapesV2S2C::chunk,
        ByteBufCodecs.BOOL, BlockShapesV2S2C::replace,
        BlockShapesS2C.Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), BlockShapesV2S2C::entries,
        BlockShapesV2S2C::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
