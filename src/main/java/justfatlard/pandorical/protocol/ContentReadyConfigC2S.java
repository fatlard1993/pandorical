package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Configuration-phase acknowledgment from client.
 * Sent after the client has registered all blocks/items from SyncContentConfigS2C
 * and loaded all assets from SyncAssetsConfigS2C.
 *
 * The server completes the PandoricalSyncTask upon receiving this,
 * allowing Fabric's SynchronizeRegistriesTask to run next.
 */
public record ContentReadyConfigC2S() implements CustomPacketPayload {
    public static final Type<ContentReadyConfigC2S> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "content_ready_config"));

    public static final StreamCodec<ByteBuf, ContentReadyConfigC2S> STREAM_CODEC =
        StreamCodec.unit(new ContentReadyConfigC2S());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
