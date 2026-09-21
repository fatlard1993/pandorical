package justfatlard.pandorical.api;

/**
 * A player's window, in the scaled pixels a screen is laid out in: a screen built to these numbers
 * fits that player's window, whatever their GUI scale.
 *
 * <p>{@link #LEAST} is the smallest window vanilla allows at any scale. It is what a player stands
 * at until their client reports one, and what a client that never reports keeps.
 */
public record Viewport(int width, int height) {
    public static final Viewport LEAST = new Viewport(320, 240);
}
