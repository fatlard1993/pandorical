package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Draw this entity with a registered model.
 *
 * @param model     the model's id, {@code namespace:path} for
 *                  {@code assets/namespace/pandorical/entity_models/path.json}; empty clears
 * @param texture   a full texture id to draw it with, or empty for the model's own, or the entity's
 * @param drawScale multiplies the size it is drawn at, and not its hitbox
 */
public record EntityModelS2C(int entityId, String model, String texture, float drawScale) implements CustomPacketPayload {

    public static final Type<EntityModelS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "entity_model"));

    public static final StreamCodec<ByteBuf, EntityModelS2C> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, EntityModelS2C::entityId,
        ByteBufCodecs.stringUtf8(256), EntityModelS2C::model,
        ByteBufCodecs.stringUtf8(256), EntityModelS2C::texture,
        ByteBufCodecs.FLOAT, EntityModelS2C::drawScale,
        EntityModelS2C::new
    );

    public static EntityModelS2C clear(int entityId) {
        return new EntityModelS2C(entityId, "", "", 1F);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
