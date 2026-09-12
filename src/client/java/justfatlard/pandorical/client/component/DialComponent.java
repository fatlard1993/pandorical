package justfatlard.pandorical.client.component;

import com.mojang.blaze3d.platform.InputConstants;
import justfatlard.pandorical.api.ComponentType;
import justfatlard.pandorical.protocol.ComponentDef;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.sdl.SDLMouse;

import java.util.Locale;
import java.util.Map;

/** See {@link ComponentType#DIAL}. A push freezes the angle until release. */
public class DialComponent extends SpriteComponent {
    private static final long AIM_EVERY_MS = 40;
    private static final float AIM_STEP_DEGREES = 0.5F;
    private static final float SHAKE_DEGREES = 7F;
    private static final float MOUSE_DEGREES_PER_PIXEL = 0.16F;
    private static final float KEY_DEGREES_PER_SECOND = 70F;
    /** Travel above this in one frame is a pointer warp, as on screen open, not movement. */
    private static final double WARP_PIXELS = 300;

    private static final int[] LEFT_KEYS = {InputConstants.KEY_A, InputConstants.KEY_LEFT};
    private static final int[] RIGHT_KEYS = {InputConstants.KEY_D, InputConstants.KEY_RIGHT};
    private static final int[] PUSH_KEYS = {InputConstants.KEY_SPACE, InputConstants.KEY_W,
        InputConstants.KEY_UP, InputConstants.KEY_RETURN};

    private float sweep = 140F;
    /** 0 to 1. */
    private float shake;
    private float angle;
    private boolean held;
    private boolean heldByKey;
    private long lastAimAt;
    private float lastAimAngle = Float.NaN;
    private double lastMouseX;
    private long lastFrameNanos;

    @Override
    public void init(ComponentDef def, ComponentContext context) {
        super.init(def, context);
        readProps();
        lastMouseX = context.minecraft().mouseHandler.xpos();
        holdCursor();
    }

    private boolean cursorHidden;

    private void holdCursor() {
        boolean hide = visible && !"hud".equals(context.screenType());
        if (hide == cursorHidden) return;
        if (hide) SDLMouse.SDL_HideCursor();
        else SDLMouse.SDL_ShowCursor();
        cursorHidden = hide;
    }

    @Override
    public void carryOverFrom(PandoricalComponent previous) {
        angle = Math.clamp(((DialComponent) previous).angle, -sweep / 2F, sweep / 2F);
    }

    @Override
    public void removed() {
        if (cursorHidden) SDLMouse.SDL_ShowCursor();
        cursorHidden = false;
    }

    @Override
    public void updateProps(Map<String, String> changedProps) {
        super.updateProps(changedProps);
        readProps();
        holdCursor();
    }

    private void readProps() {
        sweep = parseFloat(ComponentType.PROP_SWEEP, 140F);
        String trembling = props.get(ComponentType.PROP_SHAKE);
        shake = "true".equalsIgnoreCase(trembling) ? 1F
            : "false".equalsIgnoreCase(trembling) ? 0F
            : Math.clamp(parseFloat(ComponentType.PROP_SHAKE, 0F), 0F, 1F);
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float seconds = lastFrameNanos == 0 ? 0F : (now - lastFrameNanos) / 1.0e9F;
        lastFrameNanos = now;

        // Window pixels, not the scaled GUI coordinates, which round small movements away.
        double x = context.minecraft().mouseHandler.xpos();
        double travel = x - lastMouseX;
        lastMouseX = x;
        if (Math.abs(travel) > WARP_PIXELS) travel = 0;

        if (!held) {
            float moved = (float) (travel * MOUSE_DEGREES_PER_PIXEL)
                + keyDirection() * KEY_DEGREES_PER_SECOND * seconds;
            if (moved != 0F) angle = Math.clamp(angle + moved, -sweep / 2F, sweep / 2F);
            aim();
        }
        super.render(graphics, mouseX, mouseY, delta);
    }

    private static float keyDirection() {
        float direction = 0F;
        if (anyDown(LEFT_KEYS)) direction -= 1F;
        if (anyDown(RIGHT_KEYS)) direction += 1F;
        return direction;
    }

    private static boolean anyDown(int[] keys) {
        for (int key : keys) {
            if (InputConstants.isKeyDown(key)) return true;
        }
        return false;
    }

    private static boolean isPushKey(int keyCode) {
        for (int key : PUSH_KEYS) {
            if (key == keyCode) return true;
        }
        return false;
    }

    @Override
    public GeometrySnapshot displayedGeometry(float partialTick) {
        GeometrySnapshot g = interpolatedGeometry(partialTick);
        float shown = g.rotation() + angle;
        if (shake > 0F) {
            double period = 9.0e6 - 6.0e6 * shake;
            shown += (float) Math.sin(System.nanoTime() / period) * SHAKE_DEGREES * (0.15F + 0.85F * shake);
        }
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

    /** Anywhere on the screen, since the pointer is hidden. */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT) return false;
        press();
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT || !held || heldByKey) return false;
        release();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isPushKey(keyCode) || held) return false;
        heldByKey = true;
        press();
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (heldByKey && !anyDown(PUSH_KEYS)) release();
    }
}
