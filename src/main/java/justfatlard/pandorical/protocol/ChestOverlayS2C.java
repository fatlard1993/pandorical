package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * {@code texture} is a sprite base in the chests atlas, with no {@code _left}/{@code _right}
 * suffix and no extension; the client appends the suffix. The vanilla chests atlas reads a
 * directory, so any namespace's {@code textures/entity/chest/} works. {@code positions} are
 * {@code BlockPos.asLong}.
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
