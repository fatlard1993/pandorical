package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wire form of {@link justfatlard.pandorical.api.BlockEntry}: a block's position relative to
 * a structure's origin plus its full block state.
 */
public record StructureBlockEntry(int x, int y, int z, BlockState state) {
    public static final StreamCodec<ByteBuf, StructureBlockEntry> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, StructureBlockEntry::x,
        ByteBufCodecs.VAR_INT, StructureBlockEntry::y,
        ByteBufCodecs.VAR_INT, StructureBlockEntry::z,
        BlockStateCodec.STREAM_CODEC, StructureBlockEntry::state,
        StructureBlockEntry::new
    );
}
