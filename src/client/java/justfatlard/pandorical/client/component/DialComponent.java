package justfatlard.pandorical.client.component;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Locale;
import java.util.Map;
import justfatlard.pandorical.api.ComponentType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import justfatlard.pandorical.protocol.ComponentDef;

/**
 * A sprite worked by hand: it turns to follow the mouse about its own centre, and tells the
 * server where it is and when it is pressed.
 *
 * <p>The angle is the pointer's bearing from the sprite's centre, clamped to the sweep, and it
 * is drawn from that every frame without asking anyone; the server hears about it in reports
 * spaced far enough apart to be cheap and close enough together to be current. A press - the
 * left button or the space bar - freezes the angle where it is until the release, which is how
 * a pick in a lock behaves: you cannot move it while you are turning it. While the server says
 * it is shaking, it trembles about the frozen angle, drawn here so a jam looks like a jam
 * without a stream of updates to say so.
 */
public class DialComponent extends SpriteComponent {
    private static final long AIM_EVERY_MS = 40;
    private static final float AIM_STEP_DEGREES = 0.5F;
    private static final float SHAKE_DEGREES = 2.5F;

    private float sweep = 140F;
    private boolean shake;
    private float angle;
    private boolean held;
    private boolean heldByKey;
    private long lastAimAt;
    private float lastAimAngle = Float.NaN;

    @Override
    public void init(ComponentDef def, ComponentContext context) {
        super.init(def, context);
        readProps();
    }

    @Override
    public void updateProps(Map<String, String> changedProps) {
        super.updateProps(changedProps);
        readProps();
    }

    private void readProps() {
        sweep = parseFloat(ComponentType.PROP_SWEEP, 140F);
        shake = parseBool(ComponentType.PROP_SHAKE, false);
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (!held) {
            float cx = x + width / 2F;
            float cy = y + height / 2F;
            float bearing = (float) Math.toDegrees(Math.atan2(mouseX - cx, cy - mouseY));
            angle = Math.clamp(bearing, -sweep / 2F, sweep / 2F);
            aim();
        }
        super.render(graphics, mouseX, mouseY, delta);
    }

    /** The geometry the renderer turns this by: the live angle, trembling if the server says so. */
    @Override
    public GeometrySnapshot interpolatedGeometry(float partialTick) {
        GeometrySnapshot g = super.interpolatedGeometry(partialTick);
        float shown = angle;
        if (shake) shown += (float) Math.sin(System.nanoTime() / 6.0e6) * SHAKE_DEGREES;
        return new GeometrySnapshot(g.x(), g.y(), g.width(), g.height(), g.scale(), shown);
    }

    private void aim() {
        long now = System.currentTimeMillis();
        if (!Float.isNaN(lastAimAngle) && Math.abs(angle - lastAimAngle) < AIM_STEP_DEGREES) return;
        if (now - lastAimAt < AIM_EVERY_MS) return;
        lastAimAt = now;
        lastAimAngle = angle;
        report("aim");
    }

    private void report(String what) {
        context.sendAction().accept(id, Map.of(
            ComponentType.DIAL_ACTION, what,
            ComponentType.DIAL_ANGLE, String.format(Locale.ROOT, "%.1f", angle)));
    }

    private void press() {
        if (held) return;
        held = true;
        report("press");
    }

    private void release() {
        if (!held) return;
        held = false;
        heldByKey = false;
        report("release");
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        press();
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0 || !held || heldByKey) return false;
        release();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode != InputConstants.KEY_SPACE || held) return false;
        heldByKey = true;
        press();
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (heldByKey && !InputConstants.isKeyDown(InputConstants.KEY_SPACE)) release();
    }
}
