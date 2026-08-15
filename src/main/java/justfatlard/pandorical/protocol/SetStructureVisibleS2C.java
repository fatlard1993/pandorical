package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Shows or hides a structure without discarding its client-side block/pose state;
 * e.g. hide a ship's virtual structure while it's docked and its real placed-world blocks
 * are visible instead.
 */
public record SetStructureVisibleS2C(
    String structureId,
    boolean visible
) implements CustomPacketPayload {
    public static final Type<SetStructureVisibleS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "set_structure_visible"));

    public static final StreamCodec<ByteBuf, SetStructureVisibleS2C> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, SetStructureVisibleS2C::structureId,
        ByteBufCodecs.BOOL, SetStructureVisibleS2C::visible,
        SetStructureVisibleS2C::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
