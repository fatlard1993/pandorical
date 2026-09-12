package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** {@code texture} is a full identifier with extension; empty clears the overlay. */
public record EntityOverlayS2C(
    int entityId,
    String texture
) implements CustomPacketPayload {

    public static final Type<EntityOverlayS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "entity_overlay"));

    public static final StreamCodec<ByteBuf, EntityOverlayS2C> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, EntityOverlayS2C::entityId,
        ByteBufCodecs.stringUtf8(256), EntityOverlayS2C::texture,
        EntityOverlayS2C::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
