package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record ContainerDef(
    int slotCount,
    boolean includePlayerInventory
) {
    public static final StreamCodec<ByteBuf, ContainerDef> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, ContainerDef::slotCount,
        ByteBufCodecs.BOOL, ContainerDef::includePlayerInventory,
        ContainerDef::new
    );
}
