package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Wire form of {@link justfatlard.pandorical.api.RelPos} for contexts (like removed-block
 * lists) that need a bare position without a block state attached.
 */
public record StructureRelPos(int x, int y, int z) {
    public static final StreamCodec<ByteBuf, StructureRelPos> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, StructureRelPos::x,
        ByteBufCodecs.VAR_INT, StructureRelPos::y,
        ByteBufCodecs.VAR_INT, StructureRelPos::z,
        StructureRelPos::new
    );
}
