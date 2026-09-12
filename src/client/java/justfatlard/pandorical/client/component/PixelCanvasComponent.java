package justfatlard.pandorical.client.component;

import com.mojang.blaze3d.platform.InputConstants;
import justfatlard.pandorical.api.ComponentType;
import justfatlard.pandorical.api.PixelCanvas;
import justfatlard.pandorical.protocol.ComponentDef;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * A grid of cells painted by hand, drawn from this client's own copy so a stroke lands under the
 * brush the frame it is made.
 *
 * <p>Two copies are kept: the server's, as of the last report it acknowledged, and the one shown,
 * which is the server's with every unacknowledged report laid back on top. An acknowledgement
 * folds the reports it covers into the server's copy, by the same {@link PixelCanvas#apply} the
 * server ran, so the two agree without the cells crossing the wire; when the server does send
 * cells, they replace the copy outright and whatever is still unacknowledged is replayed over
 * them.
 */
public class PixelCanvasComponent extends AbstractComponent {
    private static final long REPORT_EVERY_MS = 50;
    private static final int FRAME_COLOR = 0xFF373737;

    private int columns = 1;
    private int rows = 1;
    private int[] palette = new int[0];
    private int ink = -1;
    private int brush = 1;

    private byte[] confirmed = new byte[1];
    private int[] confirmedSupply = null;
    private byte[] shown = new byte[1];
    private int[] shownSupply = null;

    private final List<PixelCanvas.Stroke> unacknowledged = new ArrayList<>();
    private final List<Integer> gathering = new ArrayList<>();
    private int gatheringInk;
    private int gatheringBrush;
    private int nextSeq = 1;
    private long lastReportAt;

    private boolean painting;
    private int lastCellX = -1;
    private int lastCellY = -1;

    @Override
    public void init(ComponentDef def, ComponentContext context) {
        super.init(def, context);
        readShape();
        confirmed = PixelCanvas.decode(props.getOrDefault(ComponentType.PROP_CANVAS_PIXELS, ""), columns * rows);
        confirmedSupply = readSupply();
        rebuild();
    }

    @Override
    public void updateProps(Map<String, String> changedProps) {
        super.updateProps(changedProps);
        boolean reshaped = changedProps.containsKey(ComponentType.PROP_CANVAS_COLUMNS)
            || changedProps.containsKey(ComponentType.PROP_CANVAS_ROWS);
        readShape();

        String ack = changedProps.get(ComponentType.PROP_CANVAS_ACK);
        if (ack != null) {
            try {
                fold(Integer.parseInt(ack));
            } catch (NumberFormatException ignored) {
            }
        }
        if (reshaped || changedProps.containsKey(ComponentType.PROP_CANVAS_PIXELS)) {
            confirmed = PixelCanvas.decode(props.getOrDefault(ComponentType.PROP_CANVAS_PIXELS, ""), columns * rows);
        }
        if (changedProps.containsKey(ComponentType.PROP_CANVAS_SUPPLY)
            || changedProps.containsKey(ComponentType.PROP_CANVAS_PALETTE)) {
            confirmedSupply = readSupply();
        }
        rebuild();
    }

    private void readShape() {
        columns = Math.max(1, parseInt(ComponentType.PROP_CANVAS_COLUMNS, 1));
        rows = Math.max(1, parseInt(ComponentType.PROP_CANVAS_ROWS, 1));
        ink = parseInt(ComponentType.PROP_CANVAS_INK, -1);
        brush = Math.max(1, parseInt(ComponentType.PROP_CANVAS_BRUSH, 1));
        String raw = props.getOrDefault(ComponentType.PROP_CANVAS_PALETTE, "");
        String[] entries = raw.isEmpty() ? new String[0] : raw.split(",");
        palette = new int[entries.length];
        for (int i = 0; i < entries.length; i++) palette[i] = color(entries[i].trim());
    }

    private int[] readSupply() {
        String raw = props.get(ComponentType.PROP_CANVAS_SUPPLY);
        return raw == null ? null : PixelCanvas.decodeSupply(raw, palette.length);
    }

    private static int color(String value) {
        try {
            String hex = value.startsWith("#") ? value.substring(1) : value;
            long parsed = Long.parseLong(hex, 16);
            if (hex.length() <= 6) parsed |= 0xFF000000L;
            return (int) parsed;
        } catch (NumberFormatException e) {
            return 0xFFFF00FF;
        }
    }

    /** Lay every report the server has now applied onto the server's copy, in the order it applied them. */
    private void fold(int ack) {
        while (!unacknowledged.isEmpty() && unacknowledged.get(0).seq() <= ack) {
            PixelCanvas.apply(confirmed, columns, rows, confirmedSupply, unacknowledged.remove(0), null);
        }
    }

    private void rebuild() {
        shown = Arrays.copyOf(confirmed, columns * rows);
        shownSupply = confirmedSupply == null ? null : confirmedSupply.clone();
        for (PixelCanvas.Stroke stroke : unacknowledged) {
            PixelCanvas.apply(shown, columns, rows, shownSupply, stroke, null);
        }
        if (!gathering.isEmpty()) {
            PixelCanvas.apply(shown, columns, rows, shownSupply, gatheredStroke(0), null);
        }
    }

    private PixelCanvas.Stroke gatheredStroke(int seq) {
        int[] anchors = new int[gathering.size()];
        for (int i = 0; i < anchors.length; i++) anchors[i] = gathering.get(i);
        return new PixelCanvas.Stroke(seq, gatheringInk, gatheringBrush, anchors);
    }

    // --- Geometry ---

    private int cellSize() {
        return Math.max(1, Math.min(width / columns, height / rows));
    }

    private int gridLeft() {
        return x + (width - cellSize() * columns) / 2;
    }

    private int gridTop() {
        return y + (height - cellSize() * rows) / 2;
    }

    private int cellX(double mouseX) {
        return (int) Math.floor((mouseX - gridLeft()) / cellSize());
    }

    private int cellY(double mouseY) {
        return (int) Math.floor((mouseY - gridTop()) / cellSize());
    }

    private boolean inGrid(int cx, int cy) {
        return cx >= 0 && cy >= 0 && cx < columns && cy < rows;
    }

    private boolean canPaint() {
        if (ink < 0 || ink >= palette.length) return false;
        return shownSupply == null || ink >= shownSupply.length
            || shownSupply[ink] == PixelCanvas.UNLIMITED || shownSupply[ink] > 0;
    }

    // --- Drawing ---

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (painting) drag(mouseX, mouseY);

        int size = cellSize();
        int left = gridLeft();
        int top = gridTop();
        graphics.fill(left - 1, top - 1, left + size * columns + 1, top + size * rows + 1, FRAME_COLOR);

        // One fill per run of like cells along a row, not one per cell
        for (int cy = 0; cy < rows; cy++) {
            int runStart = 0;
            for (int cx = 1; cx <= columns; cx++) {
                int index = cy * columns;
                if (cx < columns && shown[index + cx] == shown[index + runStart]) continue;
                int colour = paletteColor(shown[index + runStart]);
                graphics.fill(left + runStart * size, top + cy * size, left + cx * size, top + (cy + 1) * size, colour);
                runStart = cx;
            }
        }

        int hoverX = cellX(mouseX);
        int hoverY = cellY(mouseY);
        if (inGrid(hoverX, hoverY) && canPaint()) {
            int x0 = Math.max(0, PixelCanvas.brushStart(hoverX, brush));
            int y0 = Math.max(0, PixelCanvas.brushStart(hoverY, brush));
            int x1 = Math.min(columns, PixelCanvas.brushStart(hoverX, brush) + brush);
            int y1 = Math.min(rows, PixelCanvas.brushStart(hoverY, brush) + brush);
            int preview = (paletteColor((byte) ink) & 0x00FFFFFF) | 0x99000000;
            graphics.fill(left + x0 * size, top + y0 * size, left + x1 * size, top + y1 * size, preview);
            outline(graphics, left + x0 * size, top + y0 * size, left + x1 * size, top + y1 * size);
        }
    }

    private int paletteColor(byte cell) {
        int index = cell & 0xFF;
        return index < palette.length ? palette[index] : 0xFFFF00FF;
    }

    private static void outline(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
        int light = 0xC0FFFFFF;
        graphics.fill(x0, y0, x1, y0 + 1, light);
        graphics.fill(x0, y1 - 1, x1, y1, light);
        graphics.fill(x0, y0, x0 + 1, y1, light);
        graphics.fill(x1 - 1, y0, x1, y1, light);
    }

    // --- Painting ---

    private void drag(int mouseX, int mouseY) {
        int cx = cellX(mouseX);
        int cy = cellY(mouseY);
        if (!inGrid(cx, cy)) {
            lastCellX = -1;
            return;
        }
        if (cx == lastCellX && cy == lastCellY) return;
        if (lastCellX < 0) {
            dab(cx, cy);
        } else {
            line(lastCellX, lastCellY, cx, cy);
        }
        lastCellX = cx;
        lastCellY = cy;
    }

    /** Every cell between two, so a quick sweep leaves a line rather than a trail of dots. */
    private void line(int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        int x = x0;
        int y = y0;
        while (x != x1 || y != y1) {
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y += sy;
            }
            dab(x, y);
        }
    }

    private void dab(int cx, int cy) {
        if (!gathering.isEmpty() && (gatheringInk != ink || gatheringBrush != brush)) report();
        if (gathering.isEmpty()) {
            gatheringInk = ink;
            gatheringBrush = brush;
        }
        gathering.add(cx);
        gathering.add(cy);
        PixelCanvas.apply(shown, columns, rows, shownSupply,
            new PixelCanvas.Stroke(0, gatheringInk, gatheringBrush, new int[] {cx, cy}), null);
        if (gathering.size() / 2 >= PixelCanvas.MAX_ANCHORS_PER_REPORT) report();
    }

    private void report() {
        if (gathering.isEmpty()) return;
        PixelCanvas.Stroke stroke = gatheredStroke(nextSeq++);
        gathering.clear();
        unacknowledged.add(stroke);
        lastReportAt = System.currentTimeMillis();
        context.sendAction().accept(id, stroke.toAction());
    }

    @Override
    public void tick() {
        super.tick();
        if (!gathering.isEmpty() && System.currentTimeMillis() - lastReportAt >= REPORT_EVERY_MS) report();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int cx = cellX(mouseX);
        int cy = cellY(mouseY);
        if (!inGrid(cx, cy)) return false;
        if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
            context.sendAction().accept(id, Map.of(PixelCanvas.ACTION_PICK, String.valueOf(shown[cx + cy * columns] & 0xFF)));
            return true;
        }
        if (button != InputConstants.MOUSE_BUTTON_LEFT || !canPaint()) return false;
        painting = true;
        lastCellX = cx;
        lastCellY = cy;
        dab(cx, cy);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT || !painting) return false;
        painting = false;
        lastCellX = -1;
        report();
        return true;
    }

    @Override
    public void removed() {
        report();
    }

    /** The server's copy as folded here, and every report still in flight, which no prop carries. */
    @Override
    public void inherit(PandoricalComponent previous) {
        PixelCanvasComponent old = (PixelCanvasComponent) previous;
        confirmed = old.confirmed;
        confirmedSupply = old.confirmedSupply;
        unacknowledged.addAll(old.unacknowledged);
        nextSeq = old.nextSeq;
        rebuild();
    }

    @Override
    public boolean isNavigable() {
        return true;
    }
}
