package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import justfatlard.pandorical.api.CellsDrawing;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Blocks of a structure drawn as a build in their place. Sent after the spawn or block update that
 * placed them; a block replaced or removed afterwards loses its drawing on the client.
 */
public record SetStructureDrawingsS2C(
    String structureId,
    List<Entry> entries
) implements CustomPacketPayload {
    public static final Type<SetStructureDrawingsS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "structure_drawings"));

    private static final int MAX_CELLS = CellsDrawing.MAX_SIZE * CellsDrawing.MAX_SIZE * CellsDrawing.MAX_SIZE;

    /** One block's drawing, as {@link CellsDrawing} lays it out. */
    public record Entry(int x, int y, int z, int n, int height, List<BlockState> palette, byte[] cells, float lift, int turns) {
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.of(Entry::write, Entry::read);

        public static Entry of(int x, int y, int z, CellsDrawing drawing) {
            return new Entry(x, y, z, drawing.n(), drawing.height(), drawing.palette(), drawing.cells(), drawing.lift(), drawing.turns());
        }

        private static void write(ByteBuf buf, Entry entry) {
            ByteBufCodecs.VAR_INT.encode(buf, entry.x);
            ByteBufCodecs.VAR_INT.encode(buf, entry.y);
            ByteBufCodecs.VAR_INT.encode(buf, entry.z);
            ByteBufCodecs.VAR_INT.encode(buf, entry.n);
            ByteBufCodecs.VAR_INT.encode(buf, entry.height);
            ByteBufCodecs.VAR_INT.encode(buf, entry.palette.size());
            for (BlockState state : entry.palette) BlockStateCodec.STREAM_CODEC.encode(buf, state);
            ByteBufCodecs.byteArray(MAX_CELLS).encode(buf, entry.cells);
            ByteBufCodecs.FLOAT.encode(buf, entry.lift);
            ByteBufCodecs.VAR_INT.encode(buf, entry.turns);
        }

        private static Entry read(ByteBuf buf) {
            int x = ByteBufCodecs.VAR_INT.decode(buf);
            int y = ByteBufCodecs.VAR_INT.decode(buf);
            int z = ByteBufCodecs.VAR_INT.decode(buf);
            int n = ByteBufCodecs.VAR_INT.decode(buf);
            int height = ByteBufCodecs.VAR_INT.decode(buf);
            int states = ByteBufCodecs.VAR_INT.decode(buf);
            if (states < 0 || states > CellsDrawing.MAX_STATES) throw new IllegalArgumentException("a drawing with " + states + " states");
            BlockState[] palette = new BlockState[states];
            for (int i = 0; i < states; i++) palette[i] = BlockStateCodec.STREAM_CODEC.decode(buf);
            byte[] cells = ByteBufCodecs.byteArray(MAX_CELLS).decode(buf);
            float lift = ByteBufCodecs.FLOAT.decode(buf);
            int turns = ByteBufCodecs.VAR_INT.decode(buf);
            return new Entry(x, y, z, n, height, List.of(palette), cells, lift, turns);
        }
    }

    public static final StreamCodec<ByteBuf, SetStructureDrawingsS2C> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, SetStructureDrawingsS2C::structureId,
        Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), SetStructureDrawingsS2C::entries,
        SetStructureDrawingsS2C::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
