package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/** The cells of a structure the sea is kept out of; empty for none. Replaces any sent before. */
public record SetStructureDryS2C(
    String structureId,
    List<StructureRelPos> cells
) implements CustomPacketPayload {
    public static final Type<SetStructureDryS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "set_structure_dry"));

    public static final StreamCodec<ByteBuf, SetStructureDryS2C> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, SetStructureDryS2C::structureId,
        StructureRelPos.STREAM_CODEC.apply(ByteBufCodecs.list()), SetStructureDryS2C::cells,
        SetStructureDryS2C::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
