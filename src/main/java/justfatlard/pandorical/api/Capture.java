package justfatlard.pandorical.api;

/**
 * A picture a client took of its view, square.
 *
 * @param argb the pixels, row-major from the top left, {@code side * side} of them, opaque
 * @param png  the picture as the client sent it, a PNG file's bytes, for keeping as it came
 */
public record Capture(int side, int[] argb, byte[] png) {}
