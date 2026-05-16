package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Describes a container screen's inventory layout.
 * When present in OpenScreenS2C, the client creates a PandoricalContainerScreen
 * with a dynamic PandoricalMenu.
 */
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
