package justfatlard.pandorical.api;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.Base64;

/**
 * The rule a {@link ComponentType#PIXEL_CANVAS} paints by, and its wire forms.
 *
 * <p>One rule for both ends. The client lays a stroke the moment the hand moves and the server
 * lays the same stroke when the report arrives; they agree only because both call
 * {@link #apply} here, over the same cells and the same supply. A mod answering a canvas reads
 * the report with {@link Stroke#fromAction}, applies it to its own copy, and replies with
 * {@link ComponentType#PROP_CANVAS_ACK} (and {@link ComponentType#PROP_CANVAS_SUPPLY} when ink
 * is finite). It sends {@link ComponentType#PROP_CANVAS_PIXELS} only when its copy has moved
 * some way the client could not have predicted: a stroke it refused, or a change from anywhere
 * but this canvas.
 *
 * <p>Cells are palette indices, one byte each, read unsigned: a palette can have up to 256
 * colours. Row-major from the top left.
 *
 * <p>New in 1.3.9 and shaped around one user so far; it may still change shape.
 */
public final class PixelCanvas {
    private PixelCanvas() {}

    public static final int MAX_PALETTE = 256;

    /** A supply entry that never runs out. */
    public static final int UNLIMITED = -1;

    /** The widest brush {@link #apply} lays; a report asking for more gets this. */
    public static final int MAX_BRUSH = 16;

    /** Brush anchors a client puts in one report, which keeps the report inside an action value. */
    public static final int MAX_ANCHORS_PER_REPORT = 100;

    public static final String ACTION_SEQ = "seq";
    public static final String ACTION_INK = "ink";
    public static final String ACTION_BRUSH = "brush";
    /** Brush anchors as {@code x,y;x,y;...}, in the order the hand crossed them. */
    public static final String ACTION_ANCHORS = "anchors";
    /** Sent alone, instead of a stroke: the palette index under a right-click. */
    public static final String ACTION_PICK = "pick";

    /** The cells as base64. */
    public static String encode(byte[] cells) {
        return Base64.getEncoder().encodeToString(cells);
    }

    /** Exactly {@code size} cells: short or unreadable input is padded with index 0. */
    public static byte[] decode(String encoded, int size) {
        byte[] cells = new byte[size];
        try {
            byte[] read = Base64.getDecoder().decode(encoded);
            System.arraycopy(read, 0, cells, 0, Math.min(size, read.length));
        } catch (IllegalArgumentException ignored) {
        }
        return cells;
    }

    /** The inks there is any of, as {@code index:amount} pairs; every index left out has none. */
    public static String encodeSupply(int[] supply) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < supply.length; i++) {
            if (supply[i] == 0) continue;
            if (!out.isEmpty()) out.append(',');
            out.append(i).append(':').append(supply[i]);
        }
        return out.toString();
    }

    /** Exactly {@code size} entries, nought wherever the encoding names none. */
    public static int[] decodeSupply(String encoded, int size) {
        int[] supply = new int[size];
        if (encoded == null || encoded.isEmpty()) return supply;
        for (String pair : encoded.split(",")) {
            int colon = pair.indexOf(':');
            if (colon < 0) continue;
            try {
                int index = Integer.parseInt(pair.substring(0, colon).trim());
                if (index >= 0 && index < size) supply[index] = Integer.parseInt(pair.substring(colon + 1).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return supply;
    }

    /** The first column a brush of this size covers, from its anchor: centred, the odd cell going right. */
    public static int brushStart(int anchor, int brush) {
        return anchor - (brush - 1) / 2;
    }

    /**
     * Lay a stroke over {@code cells}: each anchor in order, each covering a brush-by-brush square
     * from {@link #brushStart}, clipped to the grid. A cell already the stroke's ink costs nothing.
     * Any other cell costs one from {@code supply[ink]}, and is left alone when the supply is spent,
     * so a stroke that runs dry stops where the paint did. The brush is held to 1..{@link #MAX_BRUSH}.
     *
     * @param supply  per palette index, decremented in place; null for free, {@link #UNLIMITED} for an
     *                ink that never runs out, and an ink past its end has none
     * @param changed told the index of every cell that changed, in the order they did; may be null
     * @return how many cells changed
     */
    public static int apply(byte[] cells, int width, int height, int[] supply, Stroke stroke, IntConsumer changed) {
        int ink = stroke.ink();
        if (ink < 0 || ink >= MAX_PALETTE) return 0;
        if (supply != null && ink >= supply.length) return 0;
        int brush = Math.clamp(stroke.brush(), 1, MAX_BRUSH);
        int[] anchors = stroke.anchors();
        int count = 0;
        for (int a = 0; a + 1 < anchors.length; a += 2) {
            int x0 = brushStart(anchors[a], brush);
            int y0 = brushStart(anchors[a + 1], brush);
            for (int y = Math.max(0, y0); y < Math.min(height, y0 + brush); y++) {
                for (int x = Math.max(0, x0); x < Math.min(width, x0 + brush); x++) {
                    int i = x + y * width;
                    if ((cells[i] & 0xFF) == ink) continue;
                    if (supply != null && supply[ink] != UNLIMITED) {
                        if (supply[ink] <= 0) continue;
                        supply[ink]--;
                    }
                    cells[i] = (byte) ink;
                    count++;
                    if (changed != null) changed.accept(i);
                }
            }
        }
        return count;
    }

    /**
     * One report from a canvas: the anchors the hand crossed since the last one, and the ink and
     * brush it saw while crossing them.
     *
     * @param anchors flattened {@code x0, y0, x1, y1, ...}
     */
    public record Stroke(int seq, int ink, int brush, int[] anchors) {
        /** The report in an action's data, or null when it is not one or is malformed. */
        public static Stroke fromAction(Map<String, String> data) {
            try {
                int seq = Integer.parseInt(data.getOrDefault(ACTION_SEQ, ""));
                int ink = Integer.parseInt(data.getOrDefault(ACTION_INK, ""));
                int brush = Integer.parseInt(data.getOrDefault(ACTION_BRUSH, ""));
                String raw = data.getOrDefault(ACTION_ANCHORS, "");
                String[] points = raw.isEmpty() ? new String[0] : raw.split(";");
                if (points.length > MAX_ANCHORS_PER_REPORT) return null;
                int[] anchors = new int[points.length * 2];
                for (int i = 0; i < points.length; i++) {
                    int comma = points[i].indexOf(',');
                    if (comma < 0) return null;
                    anchors[i * 2] = Integer.parseInt(points[i].substring(0, comma));
                    anchors[i * 2 + 1] = Integer.parseInt(points[i].substring(comma + 1));
                }
                return new Stroke(seq, ink, brush, anchors);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        public Map<String, String> toAction() {
            StringBuilder points = new StringBuilder();
            for (int i = 0; i + 1 < anchors.length; i += 2) {
                if (i > 0) points.append(';');
                points.append(anchors[i]).append(',').append(anchors[i + 1]);
            }
            Map<String, String> data = new HashMap<>();
            data.put(ACTION_SEQ, String.valueOf(seq));
            data.put(ACTION_INK, String.valueOf(ink));
            data.put(ACTION_BRUSH, String.valueOf(brush));
            data.put(ACTION_ANCHORS, points.toString());
            return data;
        }
    }
}
