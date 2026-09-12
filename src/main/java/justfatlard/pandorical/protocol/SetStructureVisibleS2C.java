package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Hiding keeps the client's block and pose state. */
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
