package justfatlard.pandorical.api;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The ground under a framed map, as blocks: a stack standing on each pixel, from the relief's
 * floor up, and the biome that tints it. A block here is a voxel a pixel wide; what it stands
 * for in the world, one block or a pixel's worth of them, is the caller's business.
 *
 * <p>Columns are row-major like the map's colours ({@code x + y * SIDE}, y being the map's down).
 * Stored as runs, bottom up, so the stone under a mountain costs one entry and not a hundred.
 */
public final class MapTerrain {
    public static final int SIDE = MapReliefApi.SIDE;
    /** Tallest a stack may be, which is taller than any world. */
    public static final int MOST_HEIGHT = 1024;
    /** The block index of empty space: nothing drawn there. */
    public static final int EMPTY = 0;

    private final List<BlockState> blocks;
    private final List<Identifier> biomes;
    private final int[] columnBiome;
    private final int[] runStart;
    private final int[] runs;
    private final int height;

    private MapTerrain(List<BlockState> blocks, List<Identifier> biomes, int[] columnBiome, int[] runStart, int[] runs) {
        this.blocks = blocks;
        this.biomes = biomes;
        this.columnBiome = columnBiome;
        this.runStart = runStart;
        this.runs = runs;
        int tallest = 0;
        for (int column = 0; column < SIDE * SIDE; column++) {
            int stack = 0;
            for (int run = runStart[column]; run < runStart[column + 1]; run++) stack += runs[run * 2 + 1];
            tallest = Math.max(tallest, stack);
        }
        this.height = tallest;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Put back together from its parts, as {@link #blocks}, {@link #biomes} and the run
     * accessors take it apart: for reading one off the wire.
     *
     * @throws IllegalArgumentException if the parts do not describe {@code SIDE * SIDE} columns
     *                                  of known blocks and biomes, no taller than {@link #MOST_HEIGHT}
     */
    public static MapTerrain of(List<BlockState> blocks, List<Identifier> biomes, int[] columnBiome, int[] runStart, int[] runs) {
        if (columnBiome.length != SIDE * SIDE || runStart.length != SIDE * SIDE + 1 || runStart[0] != 0
                || runStart[SIDE * SIDE] * 2 != runs.length || blocks.isEmpty() || blocks.get(EMPTY) != null) {
            throw new IllegalArgumentException("not the shape of a " + SIDE + "x" + SIDE + " terrain");
        }
        for (int column = 0; column < SIDE * SIDE; column++) {
            if (runStart[column + 1] < runStart[column]) throw new IllegalArgumentException("column " + column + " runs backwards");
            if (columnBiome[column] < 0 || columnBiome[column] >= Math.max(1, biomes.size())) {
                throw new IllegalArgumentException("column " + column + " names biome " + columnBiome[column]);
            }
        }
        long total = 0;
        for (int run = 0; run < runs.length / 2; run++) {
            if (runs[run * 2] < 0 || runs[run * 2] >= blocks.size() || runs[run * 2 + 1] < 1) {
                throw new IllegalArgumentException("run " + run + " is " + runs[run * 2 + 1] + " of block " + runs[run * 2]);
            }
            total += runs[run * 2 + 1];
        }
        MapTerrain terrain = new MapTerrain(Collections.unmodifiableList(new ArrayList<>(blocks)), List.copyOf(biomes),
            columnBiome.clone(), runStart.clone(), runs.clone());
        if (terrain.height > MOST_HEIGHT || total > (long) SIDE * SIDE * MOST_HEIGHT) {
            throw new IllegalArgumentException("a stack " + terrain.height + " tall");
        }
        return terrain;
    }

    /** Every block the terrain uses, by index; index {@link #EMPTY} is null. */
    public List<BlockState> blocks() {
        return blocks;
    }

    /** Every biome the terrain uses, by index. Empty when no column was given one. */
    public List<Identifier> biomes() {
        return biomes;
    }

    /** The tallest stack. */
    public int height() {
        return height;
    }

    public int biomeOf(int column) {
        return columnBiome[column];
    }

    /** The column's first run; its last is the one before the next column's first. */
    public int firstRun(int column) {
        return runStart[column];
    }

    public int runBlock(int run) {
        return runs[run * 2];
    }

    public int runLength(int run) {
        return runs[run * 2 + 1];
    }

    /**
     * The same ground on a floor {@code by} blocks deeper, every column's lowest block carried
     * further down: for maps laid side by side to stand from one floor, so that the ground on one
     * meets the ground on the next at the same height.
     */
    public MapTerrain lowered(int by) {
        if (by <= 0) return this;
        int[] deeper = runs.clone();
        for (int column = 0; column < SIDE * SIDE; column++) {
            if (runStart[column + 1] > runStart[column]) deeper[runStart[column] * 2 + 1] += by;
        }
        return of(blocks, biomes, columnBiome, runStart, deeper);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MapTerrain that && blocks.equals(that.blocks) && biomes.equals(that.biomes)
            && Arrays.equals(columnBiome, that.columnBiome) && Arrays.equals(runStart, that.runStart)
            && Arrays.equals(runs, that.runs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(blocks, biomes, Arrays.hashCode(runStart), Arrays.hashCode(runs));
    }

    /** Stacks blocks column by column, bottom up. Runs of one block are joined as they are laid. */
    public static final class Builder {
        private final List<BlockState> blocks = new ArrayList<>(Collections.singletonList(null));
        private final Map<BlockState, Integer> blockIndex = new HashMap<>();
        private final List<Identifier> biomes = new ArrayList<>();
        private final Map<Identifier, Integer> biomeIndex = new HashMap<>();
        private final int[] columnBiome = new int[SIDE * SIDE];
        private final IntArrayList[] columns = new IntArrayList[SIDE * SIDE];

        private Builder() {}

        /** Lay {@code count} of a block on top of a column's stack; null for empty space. */
        public Builder stack(int x, int y, BlockState block, int count) {
            if (count < 1) return this;
            int index = block == null ? EMPTY : blockIndex.computeIfAbsent(block, b -> {
                blocks.add(b);
                return blocks.size() - 1;
            });
            IntArrayList column = columns[column(x, y)];
            if (column == null) column = columns[column(x, y)] = new IntArrayList();
            int size = column.size();
            if (size > 0 && column.getInt(size - 2) == index) column.set(size - 1, column.getInt(size - 1) + count);
            else {
                column.add(index);
                column.add(count);
            }
            return this;
        }

        /** The biome a column is tinted by. Columns never given one take the first biome given to any. */
        public Builder biome(int x, int y, Identifier biome) {
            columnBiome[column(x, y)] = biomeIndex.computeIfAbsent(biome, b -> {
                biomes.add(b);
                return biomes.size() - 1;
            });
            return this;
        }

        public MapTerrain build() {
            int[] runStart = new int[SIDE * SIDE + 1];
            IntArrayList runs = new IntArrayList();
            for (int column = 0; column < SIDE * SIDE; column++) {
                runStart[column] = runs.size() / 2;
                IntArrayList stack = columns[column];
                if (stack == null) continue;
                // Empty space on top draws nothing, so it is not sent
                int end = stack.size();
                while (end > 0 && stack.getInt(end - 2) == EMPTY) end -= 2;
                runs.addElements(runs.size(), stack.elements(), 0, end);
            }
            runStart[SIDE * SIDE] = runs.size() / 2;
            return MapTerrain.of(blocks, biomes, columnBiome, runStart, runs.toIntArray());
        }

        private static int column(int x, int y) {
            if (x < 0 || y < 0 || x >= SIDE || y >= SIDE) throw new IndexOutOfBoundsException(x + "," + y + " is off a " + SIDE + " map");
            return x + y * SIDE;
        }
    }
}
