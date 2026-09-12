package justfatlard.pandorical.client.component;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Locale;
import java.util.Map;
import justfatlard.pandorical.api.ComponentType;
import justfatlard.pandorical.protocol.ComponentDef;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.sdl.SDLMouse;

/**
 * A sprite worked by hand: it is swept round its own centre and then pushed, and it tells the
 * server where it is and when the push begins and ends.
 *
 * <p>The sweep is the mouse's sideways travel, or a held A or D, left or right: not where the
 * pointer is, how far it has moved. The pointer is hidden while this is up, because a cursor
 * crawling across a lock is a cursor the hand tries to aim, and a pick is not aimed, it is
 * felt round. The angle is clamped to the sweep, drawn from here every frame without asking
 * anyone, and reported in steps spaced far enough apart to be cheap and close enough together
 * to be current.
 *
 * <p>A push - the left button, space, W, up or enter - freezes the angle where it is until the
 * release, which is how a pick in a lock behaves: you cannot move it while you are turning it.
 * Whatever the server turns this by (its rotation prop) is added on top, so a pick sitting in
 * a cylinder goes round with the cylinder; and while the server says it is shaking, it trembles
 * about the frozen angle, drawn here so a jam looks like a jam without a stream of updates to
 * say so.
 */
public class DialComponent extends SpriteComponent {
    private static final long AIM_EVERY_MS = 40;
    private static final float AIM_STEP_DEGREES = 0.5F;
    /** The tremble at its worst, the frame before the snap. */
    private static final float SHAKE_DEGREES = 7F;
    /** Degrees of sweep per window pixel of mouse travel: the whole arc in about a hand's width. */
    private static final float MOUSE_DEGREES_PER_PIXEL = 0.16F;
    /** Degrees per second under a held key: end to end in two seconds, slow enough to stop on a notch. */
    private static final float KEY_DEGREES_PER_SECOND = 70F;
    /**
     * Window pixels a hand cannot cross in one frame. The pointer is warped when a screen opens
     * and again when the mouse is let go of, and a warp arriving as travel would throw the pick
     * across the arc before the hand had touched it.
     */
    private static final double WARP_PIXELS = 300;

    private static final int[] LEFT_KEYS = {InputConstants.KEY_A, InputConstants.KEY_LEFT};
    private static final int[] RIGHT_KEYS = {InputConstants.KEY_D, InputConstants.KEY_RIGHT};
    private static final int[] PUSH_KEYS = {InputConstants.KEY_SPACE, InputConstants.KEY_W,
        InputConstants.KEY_UP, InputConstants.KEY_RETURN};

    private float sweep = 140F;
    /** Nought still, one about to snap. */
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

    /** Whether this dial has the pointer hidden now. */
    private boolean cursorHidden;

    /** The pointer is hidden while the dial shows on a screen; a HUD has no pointer to hide. */
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

        // Window pixels rather than the scaled GUI coordinates handed in, so a small movement
        // is a small movement and not a rounding to nothing
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

    /** The geometry the renderer turns this by: the server's turn, plus where the hand has it, trembling if the server says so. */
    @Override
    public GeometrySnapshot displayedGeometry(float partialTick) {
        GeometrySnapshot g = interpolatedGeometry(partialTick);
        float shown = g.rotation() + angle;
        if (shake > 0F) {
            // Wider and quicker as it worsens: the pick is being asked for more than it has
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

    /** Anywhere on the screen: with the pointer hidden there is nothing to aim a click at. */
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
