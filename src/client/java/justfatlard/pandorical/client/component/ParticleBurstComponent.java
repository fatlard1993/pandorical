package justfatlard.pandorical.client.component;

import justfatlard.pandorical.protocol.ComponentDef;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Map;

/** Particles orbiting the bounds' centre at {@code speed} degrees per wall-clock second. */
public class ParticleBurstComponent extends AbstractComponent {
    private static final int MAX_PARTICLES = 512;
    private static final int MAX_PARTICLE_SIZE = 32;

    private int count;
    private int particleSize;
    private float radius;
    private float speed;
    private int color;

    private float baseAngleAtLastChange;
    private long lastChangeNanos;

    @Override
    public void init(ComponentDef def, ComponentContext context) {
        super.init(def, context);
        this.baseAngleAtLastChange = parseFloat("start_angle", 0f);
        this.lastChangeNanos = System.nanoTime();
        parseStyle();
    }

    @Override
    public void updateProps(Map<String, String> changedProps) {
        float currentAngle = currentBaseAngle();
        super.updateProps(changedProps);
        parseStyle();
        this.baseAngleAtLastChange = currentAngle;
        this.lastChangeNanos = System.nanoTime();
    }

    private void parseStyle() {
        // Bounded: this drives a per-frame loop on the render thread.
        count = Math.clamp(parseInt("particle_count", 8), 1, MAX_PARTICLES);
        particleSize = Math.clamp(parseInt("particle_size", 3), 1, MAX_PARTICLE_SIZE);
        radius = parseFloat("radius", defaultRadius());
        speed = parseFloat("speed", 90f);
        color = parseColor("color", 0xFFFFFFFF);
        trackColor("color", color);
    }

    private float defaultRadius() {
        int shorter = Math.min(width, height);
        return shorter > 0 ? shorter / 2f : 10f;
    }

    private float currentBaseAngle() {
        double elapsedSeconds = (System.nanoTime() - lastChangeNanos) / 1_000_000_000.0;
        return baseAngleAtLastChange + (float) (speed * elapsedSeconds);
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int renderColor = interpolatedColor("color", 0xFFFFFFFF, delta);
        float baseAngle = currentBaseAngle();
        float cx = x + width / 2f;
        float cy = y + height / 2f;
        float step = 360f / count;
        int half = particleSize / 2;

        for (int i = 0; i < count; i++) {
            double rad = Math.toRadians(baseAngle + i * step);
            int px = Math.round(cx + radius * (float) Math.cos(rad)) - half;
            int py = Math.round(cy + radius * (float) Math.sin(rad)) - half;
            graphics.fill(px, py, px + particleSize, py + particleSize, renderColor);
        }
    }
}
