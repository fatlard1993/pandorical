package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Sent when a structure is spawned for a player, either via {@code StructureApi.spawn()}
 * while they already track the anchor entity, or when they start tracking an anchor entity
 * that already has a live structure attached.
 */
public record SpawnStructureS2C(
    String structureId,
    List<StructureBlockEntry> blocks,
    double x,
    double y,
    double z,
    float yaw,
    boolean visible
) implements CustomPacketPayload {
    public static final Type<SpawnStructureS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "spawn_structure"));

    public static final StreamCodec<ByteBuf, SpawnStructureS2C> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, SpawnStructureS2C::structureId,
        StructureBlockEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), SpawnStructureS2C::blocks,
        ByteBufCodecs.DOUBLE, SpawnStructureS2C::x,
        ByteBufCodecs.DOUBLE, SpawnStructureS2C::y,
        ByteBufCodecs.DOUBLE, SpawnStructureS2C::z,
        ByteBufCodecs.FLOAT, SpawnStructureS2C::yaw,
        ByteBufCodecs.BOOL, SpawnStructureS2C::visible,
        SpawnStructureS2C::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
