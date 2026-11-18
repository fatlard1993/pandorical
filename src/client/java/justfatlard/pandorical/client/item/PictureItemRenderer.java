package justfatlard.pandorical.client.item;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalNotification;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;

/**
 * {@code pandorical:picture}: an item drawn with a picture its custom data holds, laid flat over
 * the rectangle of the icon the model names, as a map's colours lie on a map.
 *
 * <p>The item model names the custom data key, and optionally {@code from} and {@code to}, the
 * corners in sixteenths of the icon, top left then bottom right, as a sprite's pixels count
 * (default the whole icon). Under the key: {@code columns}, {@code rows}, {@code palette}, ARGB
 * colours, and {@code cells}, one byte per cell indexing the palette from 0, row-major from the
 * top left, as {@link justfatlard.pandorical.api.Picture} holds them. A stack without that data,
 * or with data that does not parse, draws nothing; lay the model over a plain one in a
 * {@code minecraft:composite} and a stack without a picture shows the plain one.
 */
public final class PictureItemRenderer implements SpecialModelRenderer<PictureItemRenderer.Drawn> {
    /** Most cells to a side: an icon, not a wall. */
    public static final int LARGEST_SIDE = 64;

    /** In front of an {@code item/generated} sprite's face, which stands half a pixel out of the middle. */
    private static final float FRONT = 8.5F / 16 + 0.001F;

    private static final Cache<CustomData, Drawn> PARSED = CacheBuilder.newBuilder()
        .weakKeys().maximumSize(256).removalListener(PictureItemRenderer::released).build();

    private static final Drawn NONE = new Drawn(0, 0, new int[0], new byte[0]);

    private static final Codec<Drawn> RAW = RecordCodecBuilder.create(i -> i.group(
        Codec.intRange(1, LARGEST_SIDE).fieldOf("columns").forGetter(Drawn::columns),
        Codec.intRange(1, LARGEST_SIDE).fieldOf("rows").forGetter(Drawn::rows),
        Codec.INT_STREAM.fieldOf("palette").forGetter(d -> IntStream.of(d.palette)),
        Codec.BYTE_BUFFER.fieldOf("cells").forGetter(d -> ByteBuffer.wrap(d.cells))
    ).apply(i, (columns, rows, palette, cells) -> {
        byte[] bytes = new byte[cells.remaining()];
        cells.duplicate().get(bytes);
        return new Drawn(columns, rows, palette.toArray(), bytes);
    }));

    private static int nextSerial;

    /** A picture read from one stack's data, and the texture it is drawn from once first drawn. */
    public static final class Drawn {
        final int columns, rows;
        final int[] palette;
        final byte[] cells;
        @Nullable Identifier textureId;

        Drawn(int columns, int rows, int[] palette, byte[] cells) {
            this.columns = columns;
            this.rows = rows;
            this.palette = palette;
            this.cells = cells;
        }

        public int columns() {
            return columns;
        }

        public int rows() {
            return rows;
        }

        /** The colour drawn at a cell; magenta for an index past the palette, as a missing texture shows. */
        public int color(int column, int row) {
            int index = cells[column + row * columns] & 0xFF;
            return index < palette.length ? palette[index] : 0xFFFF00FF;
        }

        Identifier texture() {
            if (textureId == null) {
                Identifier id = Identifier.fromNamespaceAndPath("pandorical", "item_picture/" + nextSerial++);
                DynamicTexture texture = new DynamicTexture(id::toString, columns, rows, false);
                for (int row = 0; row < rows; row++) {
                    for (int column = 0; column < columns; column++) texture.getPixels().setPixel(column, row, color(column, row));
                }
                texture.upload();
                Minecraft.getInstance().getTextureManager().register(id, texture);
                textureId = id;
            }
            return textureId;
        }
    }

    private static void released(RemovalNotification<CustomData, Drawn> removed) {
        Drawn drawn = removed.getValue();
        if (drawn != null && drawn.textureId != null) Minecraft.getInstance().getTextureManager().release(drawn.textureId);
    }

    private final String key;
    private final float left, top, right, bottom;

    private PictureItemRenderer(String key, List<Float> from, List<Float> to) {
        this.key = key;
        this.left = from.get(0) / 16;
        this.top = 1 - from.get(1) / 16;
        this.right = to.get(0) / 16;
        this.bottom = 1 - to.get(1) / 16;
    }

    @Override
    public @Nullable Drawn extractArgument(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        Drawn drawn = PARSED.getIfPresent(data);
        if (drawn == null) {
            drawn = parse(data.copyTag().get(key));
            PARSED.put(data, drawn);
        }
        return drawn == NONE ? null : drawn;
    }

    /** What the data under an item model's key draws; null for nothing. */
    public static @Nullable Drawn read(@Nullable Tag tag) {
        Drawn drawn = parse(tag);
        return drawn == NONE ? null : drawn;
    }

    private static Drawn parse(@Nullable Tag tag) {
        if (tag == null) return NONE;
        Drawn drawn = RAW.parse(NbtOps.INSTANCE, tag).result().orElse(null);
        if (drawn == null || drawn.cells.length != drawn.columns * drawn.rows) return NONE;
        return drawn;
    }

    @Override
    public void submit(@Nullable Drawn drawn, PoseStack poseStack, SubmitNodeCollector collector,
                       int light, int overlay, boolean hasFoil, int outlineColor) {
        if (drawn == null) return;
        // Unshaded, like a map in a frame, so it shows the colours it holds whatever lights it.
        collector.submitCustomGeometry(poseStack, RenderTypes.text(drawn.texture()), (p, v) -> {
            vertex(p, v, light, left, bottom, 0, 1);
            vertex(p, v, light, right, bottom, 1, 1);
            vertex(p, v, light, right, top, 1, 0);
            vertex(p, v, light, left, top, 0, 0);
        });
    }

    private static void vertex(PoseStack.Pose p, VertexConsumer v, int light, float x, float y, float u, float uv) {
        v.addVertex(p, x, y, FRONT).setColor(0xFFFFFFFF).setUv(u, uv).setLight(light);
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        output.accept(new Vector3f(left, bottom, FRONT));
        output.accept(new Vector3f(right, bottom, FRONT));
        output.accept(new Vector3f(right, top, FRONT));
        output.accept(new Vector3f(left, top, FRONT));
    }

    public record Unbaked(String key, List<Float> from, List<Float> to) implements SpecialModelRenderer.Unbaked<Drawn> {
        private static final Codec<List<Float>> CORNER = Codec.floatRange(0, 16).listOf(2, 2);

        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("data").forGetter(Unbaked::key),
            CORNER.optionalFieldOf("from", List.of(0F, 0F)).forGetter(Unbaked::from),
            CORNER.optionalFieldOf("to", List.of(16F, 16F)).forGetter(Unbaked::to)
        ).apply(i, Unbaked::new));

        @Override
        public SpecialModelRenderer<Drawn> bake(SpecialModelRenderer.BakingContext context) {
            return new PictureItemRenderer(key, from, to);
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
