package justfatlard.pandorical.api;

import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.List;

/**
 * A build drawn in one block of a structure in place of that block: a cube cut {@code n} to a
 * side, {@code height} cells high, each cell an index into {@code palette} from 1, 0 for air, x
 * fastest, then z, then y. The same layout as the {@code pandorical:cells} item model.
 *
 * <p>Drawn at 1/n inside its block, raised by {@code lift} blocks (a top slab sits at a half),
 * and turned {@code turns} quarter turns clockwise seen from above about the block's middle, each
 * cell's block state turned with it. Only the drawing changes: the block is still the block for
 * walking on, using and keeping dry.
 *
 * @param n      cells to a side, 1 to {@link #MAX_SIZE}
 * @param height cells high, 1 to {@link #MAX_SIZE}
 * @param cells  {@code n * height * n} bytes
 * @param lift   blocks up from the block's floor, finite
 * @param turns  quarter turns clockwise, kept as 0 to 3
 */
public record CellsDrawing(int n, int height, List<BlockState> palette, byte[] cells, float lift, int turns) {
    public static final int MAX_SIZE = 16;
    public static final int MAX_STATES = 255;

    public CellsDrawing {
        if (n < 1 || n > MAX_SIZE || height < 1 || height > MAX_SIZE) {
            throw new IllegalArgumentException("a drawing is 1 to " + MAX_SIZE + " cells each way, not " + n + " by " + height);
        }
        if (cells.length != n * height * n) {
            throw new IllegalArgumentException("a drawing " + n + " across and " + height + " high has " + n * height * n + " cells, not " + cells.length);
        }
        if (palette.size() > MAX_STATES) throw new IllegalArgumentException("a drawing has at most " + MAX_STATES + " states, not " + palette.size());
        if (!Float.isFinite(lift)) throw new IllegalArgumentException("a drawing's lift must be finite: " + lift);
        palette = List.copyOf(palette);
        cells = cells.clone();
        turns = Math.floorMod(turns, 4);
    }

    /** A copy: the drawing does not change after it is made. */
    @Override
    public byte[] cells() {
        return cells.clone();
    }

    /** Bytes on the wire, about: what a batch of drawings is split by. */
    public int weight() {
        return cells.length + palette.size() * 48 + 16;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CellsDrawing that && n == that.n && height == that.height && turns == that.turns
            && Float.compare(lift, that.lift) == 0 && palette.equals(that.palette) && Arrays.equals(cells, that.cells);
    }

    @Override
    public int hashCode() {
        return ((31 * Arrays.hashCode(cells) + palette.hashCode()) * 31 + n * 17 + height) * 31 + Float.hashCode(lift) * 4 + turns;
    }

    @Override
    public String toString() {
        return "CellsDrawing[n=" + n + ", height=" + height + ", states=" + palette.size() + ", lift=" + lift + ", turns=" + turns + "]";
    }
}
