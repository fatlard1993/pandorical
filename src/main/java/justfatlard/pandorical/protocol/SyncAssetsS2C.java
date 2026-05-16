package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Sends gzipped asset data (models, textures) to the client in chunks.
 */
public record SyncAssetsS2C(
    int chunkIndex,
    int totalChunks,
    byte[] data
) implements CustomPacketPayload {
    public static final Type<SyncAssetsS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "sync_assets"));

    public static final StreamCodec<ByteBuf, SyncAssetsS2C> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, SyncAssetsS2C::chunkIndex,
        ByteBufCodecs.VAR_INT, SyncAssetsS2C::totalChunks,
        ByteBufCodecs.BYTE_ARRAY, SyncAssetsS2C::data,
        SyncAssetsS2C::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
