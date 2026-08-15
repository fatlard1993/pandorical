package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Sets or clears a per-entity texture overlay on the client. The referenced
 * entity is identified by its network id ({@code Entity.getId()}), which is
 * unique for the lifetime of a server run.
 *
 * <p>{@code texture} is the full texture identifier including extension, e.g.
 * {@code "poopsmith:textures/entity/poopsmith_gloves.png"}. An empty string
 * clears the overlay for that entity.
 *
 * <p>Only sent to clients that asserted the {@code "entity_overlays"}
 * capability in their HelloC2S, so older clients never see this payload.
 */
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
