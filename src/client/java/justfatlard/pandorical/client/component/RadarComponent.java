package justfatlard.pandorical.client.component;

import justfatlard.pandorical.api.ComponentType;
import justfatlard.pandorical.protocol.ComponentDef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dots on a disc that turns with the player. See {@link ComponentType#RADAR}.
 *
 * <p>Everything that moves with the camera is worked out here, every frame: where the player is,
 * which way they face, and where each blip sits against both. The server's part is the list, a
 * few times a second. A radar turned on the server trails the camera by a round trip every time
 * the player looks round, which is the one moment a radar is being read.
 */
public class RadarComponent extends AbstractComponent {
    private static final int DISC = 0x90000000;
    private static final int RIM = 0xC0D8D8D8;
    private static final int RING = 0x40D8D8D8;
    private static final int SELF = 0xFFFFFFFF;
    private static final int TARGET = 0xFFFFD84A;
    private static final int NEEDLE = 0xFFC9A93A;

    /** Past this far above or below, a blip is dimmed: it is near on the map and not in reach. */
    private static final double DIM_HEIGHT = 6.0;

    private record Blip(int entityId, double x, double y, double z, int color, int size, boolean person) {}

    private final List<Blip> blips = new ArrayList<>();
    private float range = 32f;
    private double targetX = Double.NaN;
    private double targetZ = Double.NaN;

    @Override
    public void init(ComponentDef def, ComponentContext context) {
        super.init(def, context);
        parseProps();
    }

    @Override
    public void updateProps(Map<String, String> changedProps) {
        super.updateProps(changedProps);
        parseProps();
    }

    private void parseProps() {
        range = Math.max(1f, parseFloat(ComponentType.PROP_RADAR_RANGE, 32f));
        targetX = coord(ComponentType.PROP_RADAR_TARGET_X);
        targetZ = coord(ComponentType.PROP_RADAR_TARGET_Z);

        blips.clear();
        String raw = props.getOrDefault(ComponentType.PROP_RADAR_BLIPS, "");
        if (raw.isEmpty()) return;
        for (String entry : raw.split(";")) {
            String[] parts = entry.split(",");
            if (parts.length < 6) continue;
            try {
                blips.add(new Blip(Integer.parseInt(parts[0]),
                    Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
                    Integer.parseInt(parts[4]), Math.clamp(Integer.parseInt(parts[5]), 1, 3),
                    parts.length > 6 && parts[6].equals("p")));
            } catch (NumberFormatException ignored) {
                // One bad entry costs that dot, not the radar.
            }
        }
    }

    private double coord(String key) {
        String value = props.get(key);
        if (value == null || value.isEmpty()) return Double.NaN;
        try { return Double.parseDouble(value); } catch (NumberFormatException e) { return Double.NaN; }
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        int size = Math.min(width, height);
        float radius = size / 2f;
        float cx = x + radius;
        float cy = y + radius;
        float perBlock = (radius - 2f) / range;

        disc(graphics, cx, cy, radius, DISC);
        circle(graphics, cx, cy, radius - 0.5f, RIM);
        circle(graphics, cx, cy, (radius - 2f) / 2f, RING);

        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 self = mc.player.getPosition(partial);
        double yaw = Math.toRadians(mc.player.getViewYRot(partial));
        // Facing yaw 0 is facing +z, and the right hand then points to -x.
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);

        for (Blip blip : blips) {
            Entity seen = mc.level.getEntity(blip.entityId());
            Vec3 at = seen != null ? seen.getPosition(partial) : new Vec3(blip.x(), blip.y(), blip.z());
            double dx = at.x - self.x, dz = at.z - self.z;
            double side = dx * rx + dz * rz;
            double ahead = dx * fx + dz * fz;
            if (side * side + ahead * ahead > (double) range * range) continue;

            int color = Math.abs(at.y - self.y) > DIM_HEIGHT ? dim(blip.color()) : blip.color();
            if (blip.person()) {
                MapComponent.diamond(graphics, Math.round(cx + (float) side * perBlock),
                    Math.round(cy - (float) ahead * perBlock), color);
                continue;
            }
            // Two, three or four pixels across: a chicken, a cow, a ravager.
            int across = blip.size() + 1;
            int px = Math.round(cx + (float) side * perBlock) - across / 2;
            int py = Math.round(cy - (float) ahead * perBlock) - across / 2;
            graphics.fill(px, py, px + across, py + across, color);
        }

        if (!Double.isNaN(targetX) && !Double.isNaN(targetZ)) {
            needle(graphics, cx, cy, radius, perBlock, targetX - self.x, targetZ - self.z, fx, fz, rx, rz);
        }

        // The player, pointing the way they face: which on this disc is always up.
        int sx = Math.round(cx), sy = Math.round(cy);
        graphics.fill(sx, sy - 2, sx + 1, sy + 2, SELF);
        graphics.fill(sx - 1, sy, sx + 2, sy + 2, SELF);
        graphics.fill(sx - 2, sy + 1, sx + 3, sy + 2, SELF);
    }

    /**
     * The compass's own job, kept: a needle from the centre toward the target. It stops at the
     * target when the target is on the disc, and at the rim with an arrowhead when it is further,
     * which is most of the time. A dot on the rim said the same thing and nobody read it as the
     * needle it replaced.
     */
    private static void needle(GuiGraphicsExtractor graphics, float cx, float cy, float radius, float perBlock,
                               double dx, double dz, double fx, double fz, double rx, double rz) {
        double side = dx * rx + dz * rz;
        double ahead = dx * fx + dz * fz;
        double far = Math.sqrt(side * side + ahead * ahead);
        if (far < 1e-6) return;

        // Screen direction of the target: right is +x, ahead is up, which is -y.
        double ux = side / far, uy = -ahead / far;
        double rim = radius - 2.5;
        double reach = far * perBlock;
        boolean beyond = reach > rim;
        double tip = beyond ? rim : reach;

        for (double along = 4; along <= tip - (beyond ? 4 : 2); along += 0.75) {
            dot(graphics, cx + ux * along, cy + uy * along, NEEDLE);
        }

        if (beyond) {
            // An arrowhead pointing out through the rim: rows across the needle, widening back
            // from the point.
            for (double back = 0; back <= 5; back += 0.5) {
                double half = back * 0.6;
                for (double across = -half; across <= half; across += 0.5) {
                    dot(graphics, cx + ux * (tip - back) - uy * across, cy + uy * (tip - back) + ux * across, TARGET);
                }
            }
        } else {
            int px = (int) Math.round(cx + ux * tip), py = (int) Math.round(cy + uy * tip);
            graphics.fill(px - 1, py - 2, px + 2, py + 3, TARGET);
            graphics.fill(px - 2, py - 1, px + 3, py + 2, TARGET);
        }
    }

    private static void dot(GuiGraphicsExtractor graphics, double x, double y, int color) {
        int px = (int) Math.floor(x), py = (int) Math.floor(y);
        graphics.fill(px, py, px + 1, py + 1, color);
    }

    private static int dim(int argb) {
        int alpha = (argb >>> 24) / 2;
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    /** A filled disc, a row at a time. */
    private static void disc(GuiGraphicsExtractor graphics, float cx, float cy, float r, int color) {
        int top = (int) Math.floor(cy - r), bottom = (int) Math.ceil(cy + r);
        for (int row = top; row < bottom; row++) {
            double dy = row + 0.5 - cy;
            double half = Math.sqrt(Math.max(0, r * r - dy * dy));
            int left = (int) Math.round(cx - half), right = (int) Math.round(cx + half);
            if (right > left) graphics.fill(left, row, right, row + 1, color);
        }
    }

    /** A one-pixel ring, stepped finely enough to leave no gaps at these sizes. */
    private static void circle(GuiGraphicsExtractor graphics, float cx, float cy, float r, int color) {
        int steps = Math.max(24, (int) (r * 7));
        int lastX = Integer.MIN_VALUE, lastY = Integer.MIN_VALUE;
        for (int i = 0; i < steps; i++) {
            double a = Math.PI * 2 * i / steps;
            int px = (int) Math.floor(cx + Math.cos(a) * r);
            int py = (int) Math.floor(cy + Math.sin(a) * r);
            if (px == lastX && py == lastY) continue;
            graphics.fill(px, py, px + 1, py + 1, color);
            lastX = px;
            lastY = py;
        }
    }
}
