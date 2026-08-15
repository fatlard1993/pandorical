package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Incremental block delta for an existing structure: blocks added, removed, and changed
 * in place (changed entries carry their new {@link net.minecraft.world.level.block.state.BlockState}
 * the same way added entries do; the client just overwrites its stored block at that position).
 */
public record UpdateStructureBlocksS2C(
    String structureId,
    List<StructureBlockEntry> added,
    List<StructureRelPos> removed,
    List<StructureBlockEntry> changed
) implements CustomPacketPayload {
    public static final Type<UpdateStructureBlocksS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "update_structure_blocks"));

    public static final StreamCodec<ByteBuf, UpdateStructureBlocksS2C> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, UpdateStructureBlocksS2C::structureId,
        StructureBlockEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), UpdateStructureBlocksS2C::added,
        StructureRelPos.STREAM_CODEC.apply(ByteBufCodecs.list()), UpdateStructureBlocksS2C::removed,
        StructureBlockEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), UpdateStructureBlocksS2C::changed,
        UpdateStructureBlocksS2C::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
