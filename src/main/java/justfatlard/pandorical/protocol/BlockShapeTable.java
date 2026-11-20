package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A block's outline and collision shapes, state by state, with each distinct shape held once.
 *
 * <p>States mostly repeat a handful of shapes: a plant's thousands of states may be five, and a
 * connecting block's outline is nearly always its collision. Each state names its two shapes by
 * index into {@link #distinct}, in the block's own state order.
 */
public record BlockShapeTable(List<Shape> distinct, int[] outline, int[] collision) {

    /** No shapes at all: the client keeps its stand-in's own geometry. */
    public static final BlockShapeTable NONE = new BlockShapeTable(List.of(), new int[0], new int[0]);

    private static final int FLOATS_PER_BOX = 6;

    /** Boxes, six floats each: min x, y, z then max x, y, z. No boxes is an empty shape. */
    public record Shape(float[] boxes) {
        public static final Shape EMPTY = new Shape(new float[0]);

        public int boxCount() {
            return boxes.length / FLOATS_PER_BOX;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Shape shape && Arrays.equals(boxes, shape.boxes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(boxes);
        }
    }

    public boolean isEmpty() {
        return outline.length == 0;
    }

    public int stateCount() {
        return outline.length;
    }

    public Shape outlineOf(int state) {
        return distinct.get(outline[state]);
    }

    public Shape collisionOf(int state) {
        return distinct.get(collision[state]);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BlockShapeTable table && distinct.equals(table.distinct)
            && Arrays.equals(outline, table.outline) && Arrays.equals(collision, table.collision);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * distinct.hashCode() + Arrays.hashCode(outline)) + Arrays.hashCode(collision);
    }

    /** Takes states in order, outline and collision for each. */
    public static final class Builder {
        private final Map<Shape, Integer> indexOf = new HashMap<>();
        private final List<Shape> distinct = new ArrayList<>();
        private final List<int[]> states = new ArrayList<>();

        public Builder add(Shape outline, Shape collision) {
            states.add(new int[] {index(outline), index(collision)});
            return this;
        }

        private int index(Shape shape) {
            return indexOf.computeIfAbsent(shape, s -> {
                distinct.add(s);
                return distinct.size() - 1;
            });
        }

        public BlockShapeTable build() {
            if (states.isEmpty()) return NONE;
            int[] outline = new int[states.size()];
            int[] collision = new int[states.size()];
            for (int i = 0; i < outline.length; i++) {
                outline[i] = states.get(i)[0];
                collision[i] = states.get(i)[1];
            }
            return new BlockShapeTable(List.copyOf(distinct), outline, collision);
        }
    }

    /**
     * The layout the first content channels carry: per state, the outline's box count and boxes,
     * then the collision's. No states is no bytes.
     */
    public byte[] perStateBytes() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        try {
            for (int state = 0; state < stateCount(); state++) {
                writeBoxes(out, outlineOf(state));
                writeBoxes(out, collisionOf(state));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    /** What {@link #perStateBytes} comes to, without building it. */
    public long perStateByteCount() {
        long count = 0;
        for (int state = 0; state < stateCount(); state++) {
            count += 2 + 24L * (outlineOf(state).boxCount() + collisionOf(state).boxCount());
        }
        return count;
    }

    private static void writeBoxes(DataOutputStream out, Shape shape) throws IOException {
        out.writeByte(shape.boxCount());
        for (float f : shape.boxes) out.writeFloat(f);
    }

    /** Reads {@link #perStateBytes}; bytes that end mid-state read as no shapes at all. */
    public static BlockShapeTable fromPerStateBytes(byte[] data) {
        if (data == null || data.length == 0) return NONE;
        Builder builder = new Builder();
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
            while (in.available() > 0) {
                Shape outline = readBoxes(in);
                builder.add(outline, readBoxes(in));
            }
        } catch (IOException e) {
            return NONE;
        }
        return builder.build();
    }

    private static Shape readBoxes(DataInputStream in) throws IOException {
        float[] boxes = new float[(in.readByte() & 0xFF) * FLOATS_PER_BOX];
        for (int i = 0; i < boxes.length; i++) boxes[i] = in.readFloat();
        return boxes.length == 0 ? Shape.EMPTY : new Shape(boxes);
    }

    /**
     * The packed layout: the distinct shapes, then each state's indices. A flag spares the
     * collision indices when every state collides with its own outline, which is most blocks.
     */
    public static final StreamCodec<ByteBuf, BlockShapeTable> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BlockShapeTable decode(ByteBuf buf) {
            int distinctCount = ByteBufCodecs.VAR_INT.decode(buf);
            List<Shape> distinct = new ArrayList<>(distinctCount);
            for (int i = 0; i < distinctCount; i++) {
                float[] boxes = new float[ByteBufCodecs.VAR_INT.decode(buf) * FLOATS_PER_BOX];
                for (int f = 0; f < boxes.length; f++) boxes[f] = buf.readFloat();
                distinct.add(boxes.length == 0 ? Shape.EMPTY : new Shape(boxes));
            }
            int states = ByteBufCodecs.VAR_INT.decode(buf);
            if (states == 0) return NONE;
            boolean collidesAsOutlined = buf.readBoolean();
            int[] outline = new int[states];
            for (int i = 0; i < states; i++) outline[i] = ByteBufCodecs.VAR_INT.decode(buf);
            int[] collision = outline;
            if (!collidesAsOutlined) {
                collision = new int[states];
                for (int i = 0; i < states; i++) collision[i] = ByteBufCodecs.VAR_INT.decode(buf);
            }
            return new BlockShapeTable(List.copyOf(distinct), outline, collision);
        }

        @Override
        public void encode(ByteBuf buf, BlockShapeTable table) {
            ByteBufCodecs.VAR_INT.encode(buf, table.distinct.size());
            for (Shape shape : table.distinct) {
                ByteBufCodecs.VAR_INT.encode(buf, shape.boxCount());
                for (float f : shape.boxes) buf.writeFloat(f);
            }
            ByteBufCodecs.VAR_INT.encode(buf, table.stateCount());
            if (table.isEmpty()) return;
            boolean collidesAsOutlined = Arrays.equals(table.outline, table.collision);
            buf.writeBoolean(collidesAsOutlined);
            for (int index : table.outline) ByteBufCodecs.VAR_INT.encode(buf, index);
            if (!collidesAsOutlined) {
                for (int index : table.collision) ByteBufCodecs.VAR_INT.encode(buf, index);
            }
        }
    };
}
