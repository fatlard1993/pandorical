package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** How large a structure is drawn: 1 is full size, a half is every block half a block across. */
public record SetStructureScaleS2C(
    String structureId,
    float scale
) implements CustomPacketPayload {
    public static final Type<SetStructureScaleS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "set_structure_scale"));

    public static final StreamCodec<ByteBuf, SetStructureScaleS2C> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, SetStructureScaleS2C::structureId,
        ByteBufCodecs.FLOAT, SetStructureScaleS2C::scale,
        SetStructureScaleS2C::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
