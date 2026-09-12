package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record StructureRelPos(int x, int y, int z) {
    public static final StreamCodec<ByteBuf, StructureRelPos> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, StructureRelPos::x,
        ByteBufCodecs.VAR_INT, StructureRelPos::y,
        ByteBufCodecs.VAR_INT, StructureRelPos::z,
        StructureRelPos::new
    );
}
