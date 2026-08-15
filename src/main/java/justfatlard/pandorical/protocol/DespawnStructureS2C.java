package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Permanently removes a structure client-side. Sent either explicitly via
 * {@code StructureApi.despawn()} or implicitly when a player stops tracking the anchor entity.
 */
public record DespawnStructureS2C(
    String structureId
) implements CustomPacketPayload {
    public static final Type<DespawnStructureS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "despawn_structure"));

    public static final StreamCodec<ByteBuf, DespawnStructureS2C> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, DespawnStructureS2C::structureId,
        DespawnStructureS2C::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
