package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Sent during the Pandorical handshake to inform the client which entity types need
 * which renderer. The client registers these renderers into EntityRenderers.PROVIDERS
 * so that entities spawned by server-only mods render correctly without those mods
 * being installed on the client.
 *
 * Supported renderer keys (case-sensitive):
 *   "thrown_item" — ThrownItemRenderer
 *   "invisible"   — NoopRenderer (renders nothing)
 */
public record EntityRenderersS2C(
    Map<String, String> renderers
) implements CustomPacketPayload {

    public static final Type<EntityRenderersS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "entity_renderers"));

    public static final StreamCodec<ByteBuf, EntityRenderersS2C> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8),
        EntityRenderersS2C::renderers,
        EntityRenderersS2C::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
