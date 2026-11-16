package justfatlard.pandorical.client.item;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@code pandorical:cells}: an item drawn as the build its custom data holds, a cube cut
 * {@code n} to a side, scaled into the one block an item model stands in.
 *
 * <p>The item model names the custom data key. Under it: {@code n}, an optional {@code height}
 * (default {@code n}, for builds shorter than they are wide, such as a slab), {@code palette}, a
 * list of block states, and {@code cells}, one byte per cell indexing the palette from 1, 0 for
 * air, x fastest, then z, then y. A stack without that data, or with data that does not parse,
 * draws nothing; wrap the model in a {@code minecraft:condition} to give it a fallback.
 */
public final class CellsItemRenderer implements SpecialModelRenderer<CellsItemRenderer.Cells> {
    public static final int MAX_SIZE = justfatlard.pandorical.api.CellsDrawing.MAX_SIZE;

    private static final BlockDisplayContext DISPLAY = BlockDisplayContext.create();

    /**
     * Parsed once per custom data instance. Weak keys compare by identity, which is what keeps
     * the argument the same object frame to frame, so the GUI's item atlas reuses its drawing.
     */
    private static final Cache<CustomData, Cells> PARSED = CacheBuilder.newBuilder()
        .weakKeys().maximumSize(512).build();

    private static final Cells NONE = new Cells(1, List.of());

    private static final Codec<Raw> RAW = RecordCodecBuilder.create(i -> i.group(
        Codec.intRange(1, MAX_SIZE).fieldOf("n").forGetter(Raw::n),
        Codec.intRange(1, MAX_SIZE).optionalFieldOf("height").forGetter(r -> java.util.Optional.of(r.height())),
        BlockState.CODEC.listOf().fieldOf("palette").forGetter(Raw::palette),
        Codec.BYTE_BUFFER.fieldOf("cells").forGetter(r -> ByteBuffer.wrap(r.cells()))
    ).apply(i, (n, height, palette, cells) -> {
        byte[] bytes = new byte[cells.remaining()];
        cells.duplicate().get(bytes);
        return new Raw(n, height.orElse(n), palette, bytes);
    }));

    private record Raw(int n, int height, List<BlockState> palette, byte[] cells) {}

    /** One cell that shows from outside, where it stands in the build. */
    public record Cell(int x, int y, int z, BlockState state) {}

    /** The cells worth drawing, and how many to a side. Compared by identity on purpose. */
    public static final class Cells {
        final int n;
        final List<Cell> showing;

        Cells(int n, List<Cell> showing) {
            this.n = n;
            this.showing = showing;
        }

        public int size() {
            return n;
        }

        public List<Cell> showing() {
            return showing;
        }
    }

    private final String key;
    private final BlockModelResolver resolver;
    private final BlockModelRenderState scratch = new BlockModelRenderState();

    private CellsItemRenderer(String key) {
        this.key = key;
        this.resolver = new BlockModelResolver(Minecraft.getInstance().getModelManager());
    }

    @Override
    public @Nullable Cells extractArgument(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        Cells cells = PARSED.getIfPresent(data);
        if (cells == null) {
            cells = parse(data.copyTag().get(key));
            PARSED.put(data, cells);
        }
        return cells == NONE ? null : cells;
    }

    /** What the data under an item model's key draws; null for nothing. */
    public static @Nullable Cells read(@Nullable Tag tag) {
        Cells cells = parse(tag);
        return cells == NONE ? null : cells;
    }

    private static Cells parse(@Nullable Tag tag) {
        if (tag == null) return NONE;
        Raw raw = RAW.parse(NbtOps.INSTANCE, tag).result().orElse(null);
        if (raw == null) return NONE;
        Cells cells = cull(raw.n(), raw.height(), raw.palette(), raw.cells());
        return cells == null ? NONE : cells;
    }

    /**
     * The cells of a build laid out as an item model's data is, less those walled in on every side
     * by solid cells; null for a layout that does not add up or a build with nothing showing. Also
     * what a structure block drawn as a build is drawn from.
     */
    public static @Nullable Cells cull(int n, int height, List<BlockState> palette, byte[] cells) {
        if (n < 1 || height < 1 || n > MAX_SIZE || height > MAX_SIZE || cells.length != n * height * n) return null;
        BlockState[] grid = new BlockState[cells.length];
        for (int i = 0; i < grid.length; i++) {
            int index = cells[i] & 0xFF;
            grid[i] = index == 0 || index > palette.size() ? null : palette.get(index - 1);
        }

        List<Cell> showing = new ArrayList<>();
        for (int y = 0; y < height; y++) for (int z = 0; z < n; z++) for (int x = 0; x < n; x++) {
            BlockState state = grid[(y * n + z) * n + x];
            if (state == null || state.isAir()) continue;
            if (hides(grid, n, height, x + 1, y, z) && hides(grid, n, height, x - 1, y, z)
                && hides(grid, n, height, x, y + 1, z) && hides(grid, n, height, x, y - 1, z)
                && hides(grid, n, height, x, y, z + 1) && hides(grid, n, height, x, y, z - 1)) continue;
            showing.add(new Cell(x, y, z, state));
        }
        return showing.isEmpty() ? null : new Cells(n, List.copyOf(showing));
    }

    private static boolean hides(BlockState[] grid, int n, int height, int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= n || y >= height || z >= n) return false;
        BlockState state = grid[(y * n + z) * n + x];
        return state != null && state.isSolidRender();
    }

    @Override
    public void submit(@Nullable Cells cells, PoseStack poseStack, SubmitNodeCollector collector,
                       int light, int overlay, boolean hasFoil, int outlineColor) {
        if (cells == null) return;
        float scale = 1.0F / cells.n;
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        for (Cell cell : cells.showing) {
            poseStack.pushPose();
            poseStack.translate(cell.x(), cell.y(), cell.z());
            scratch.clear();
            resolver.update(scratch, cell.state(), DISPLAY);
            scratch.submit(poseStack, collector, light, overlay, outlineColor);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        for (int corner = 0; corner < 8; corner++) {
            output.accept(new Vector3f(corner & 1, (corner >> 1) & 1, (corner >> 2) & 1));
        }
    }

    public record Unbaked(String key) implements SpecialModelRenderer.Unbaked<Cells> {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("data").forGetter(Unbaked::key)
        ).apply(i, Unbaked::new));

        @Override
        public SpecialModelRenderer<Cells> bake(SpecialModelRenderer.BakingContext context) {
            return new CellsItemRenderer(key);
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
