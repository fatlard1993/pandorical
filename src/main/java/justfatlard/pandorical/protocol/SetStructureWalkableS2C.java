package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** A walkable structure is solid to the local player and carries them while they stand on it. */
public record SetStructureWalkableS2C(
    String structureId,
    boolean walkable
) implements CustomPacketPayload {
    public static final Type<SetStructureWalkableS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "set_structure_walkable"));

    public static final StreamCodec<ByteBuf, SetStructureWalkableS2C> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, SetStructureWalkableS2C::structureId,
        ByteBufCodecs.BOOL, SetStructureWalkableS2C::walkable,
        SetStructureWalkableS2C::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
