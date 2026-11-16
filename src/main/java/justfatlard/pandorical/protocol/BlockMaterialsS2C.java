package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Blocks that sound and crumble like another, position by position: for each, the block state
 * it is heard and broken as, or air to forget it and go back to the block's own.
 *
 * <p>{@code replace} is the whole set for the world the player is now in, sent as they arrive in
 * it: everything the client held before is dropped first, so a material cleared while they were
 * elsewhere does not survive their coming back. A state whose block the client does not know
 * arrives as air, which forgets the position rather than guessing.
 */
public record BlockMaterialsS2C(Identifier dimension, boolean replace, List<Entry> entries) implements CustomPacketPayload {
    public static final Type<BlockMaterialsS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "block_materials"));

    public record Entry(long pos, BlockState material) {
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, Entry::pos,
            BlockStateCodec.STREAM_CODEC, Entry::material,
            Entry::new);
    }

    public static final StreamCodec<ByteBuf, BlockMaterialsS2C> STREAM_CODEC = StreamCodec.composite(
        Identifier.STREAM_CODEC, BlockMaterialsS2C::dimension,
        ByteBufCodecs.BOOL, BlockMaterialsS2C::replace,
        Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), BlockMaterialsS2C::entries,
        BlockMaterialsS2C::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
