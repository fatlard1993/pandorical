package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Tells one client that certain chests should be drawn with a different texture.
 *
 * <p>{@code texture} is a sprite base in the chests atlas, without the
 * {@code _left} / {@code _right} suffix and without an extension, e.g.
 * {@code "yourmod:entity/chest/hoard"}. The client appends the suffix for
 * whichever half of a double chest it is drawing. Any namespace works: the
 * vanilla chests atlas is built from a directory source, so a texture at
 * {@code assets/<namespace>/textures/entity/chest/<name>.png} is picked up
 * without touching the atlas definition.
 *
 * <p>{@code positions} are packed with {@link net.minecraft.core.BlockPos#asLong}.
 *
 * <p>Unlike entity overlays, which are broadcast to everyone tracking the
 * entity, these are per player: whether a chest is worth marking can depend on
 * who is looking at it.
 *
 * <p>Only sent to clients that asserted the {@code "chest_overlays"} capability
 * in their HelloC2S, so older clients never see this payload.
 */
public record ChestOverlayS2C(
    byte op,
    String texture,
    long[] positions
) implements CustomPacketPayload {

    /** Everything currently marked with this texture is replaced by {@code positions}. */
    public static final byte OP_REPLACE = 0;
    /** {@code positions} join whatever is already marked with this texture. */
    public static final byte OP_ADD = 1;
    /** {@code positions} lose their mark, whatever texture they carried. */
    public static final byte OP_REMOVE = 2;

    public static final Type<ChestOverlayS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "chest_overlay"));

    public static final StreamCodec<ByteBuf, ChestOverlayS2C> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BYTE, ChestOverlayS2C::op,
        ByteBufCodecs.stringUtf8(256), ChestOverlayS2C::texture,
        ByteBufCodecs.LONG_ARRAY, ChestOverlayS2C::positions,
        ChestOverlayS2C::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
