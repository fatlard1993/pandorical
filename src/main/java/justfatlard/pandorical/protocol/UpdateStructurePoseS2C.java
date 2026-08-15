package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Pushes a new world position/yaw for a structure. The client interpolates towards this pose
 * from the previously known one rather than snapping, so the sender should call this roughly
 * once per server tick while the structure is moving for smooth client-side motion.
 */
public record UpdateStructurePoseS2C(
    String structureId,
    double x,
    double y,
    double z,
    float yaw
) implements CustomPacketPayload {
    public static final Type<UpdateStructurePoseS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "update_structure_pose"));

    public static final StreamCodec<ByteBuf, UpdateStructurePoseS2C> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, UpdateStructurePoseS2C::structureId,
        ByteBufCodecs.DOUBLE, UpdateStructurePoseS2C::x,
        ByteBufCodecs.DOUBLE, UpdateStructurePoseS2C::y,
        ByteBufCodecs.DOUBLE, UpdateStructurePoseS2C::z,
        ByteBufCodecs.FLOAT, UpdateStructurePoseS2C::yaw,
        UpdateStructurePoseS2C::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
