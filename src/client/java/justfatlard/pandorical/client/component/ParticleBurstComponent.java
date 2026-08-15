package justfatlard.pandorical.client.component;

import justfatlard.pandorical.protocol.ComponentDef;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Map;

/**
 * A small burst of particle-like sprites, simulated entirely on the client every frame:
 * the server declares the motion pattern once (particle count, orbit radius/speed) and never
 * needs to push individual particle positions, same philosophy as vanilla's own particle systems.
 *
 * <p>Only the {@code "orbit"} motion pattern is implemented: particles are evenly spaced around a
 * circle centered on the component's bounds ({@code x + width/2}, {@code y + height/2}) and rotate
 * continuously at {@code speed} degrees/second.
 *
 * <p>Orbit phase is tracked as a real-time (wall-clock) angle rather than a server-tick-driven
 * one, so it keeps animating with no server updates. It is intentionally independent of the
 * generic geometry interpolation in {@link AbstractComponent}, which still applies on top for the
 * component's overall position/size/scale/rotation as a whole (the entire orbiting cluster can
 * itself be smoothly repositioned via x/y prop updates same as any other component).
 *
 * <p>When {@code speed}/{@code start_angle} change via {@link #updateProps}, the current animated
 * angle is captured first so the orbit continues smoothly rather than jumping.
 */
public class ParticleBurstComponent extends AbstractComponent {
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
        count = Math.max(1, parseInt("particle_count", 8));
        particleSize = Math.max(1, parseInt("particle_size", 3));
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
