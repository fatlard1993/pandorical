package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

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
