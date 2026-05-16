package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Configuration-phase asset sync payload.
 * Sends gzipped asset data (models, textures) during the configuration phase
 * so blocks/items are fully set up before Fabric's registry sync.
 *
 * Same data format as SyncAssetsS2C but registered on the configuration channel.
 */
public record SyncAssetsConfigS2C(
    int chunkIndex,
    int totalChunks,
    byte[] data
) implements CustomPacketPayload {
    public static final Type<SyncAssetsConfigS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "sync_assets_config"));

    public static final StreamCodec<ByteBuf, SyncAssetsConfigS2C> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, SyncAssetsConfigS2C::chunkIndex,
        ByteBufCodecs.VAR_INT, SyncAssetsConfigS2C::totalChunks,
        ByteBufCodecs.BYTE_ARRAY, SyncAssetsConfigS2C::data,
        SyncAssetsConfigS2C::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
