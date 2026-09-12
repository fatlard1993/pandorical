package justfatlard.pandorical.api;

/**
 * A picture to stand in the world: a grid of palette-coloured cells on a thin panel.
 *
 * <p>Cells are palette indices, row-major from the top left, as a {@link PixelCanvas} holds
 * them, so a canvas being painted on a screen and the same canvas standing in the world are one
 * array. The front shows the cells; the back and the four edges are {@code backColor}, or not
 * drawn at all when its alpha is nought.
 *
 * @param palette   ARGB colours, indexed by cell value
 * @param thickness of the panel, in blocks
 */
public record Picture(int columns, int rows, int[] palette, byte[] cells, Pose pose, int backColor, float thickness) {
    /**
     * Where the picture stands, relative to the entity it is anchored to.
     *
     * <p>{@code x}, {@code y}, {@code z} place the middle of the picture's bottom edge, in blocks
     * from the anchor's position along the world's axes. {@code yaw} turns it the way an entity's
     * yaw turns: at 0 the front faces south, at 90 west. {@code tilt} leans the top back, away
     * from whoever is looking at the front, in degrees. {@code width} and {@code height} are its
     * size in blocks; cells are stretched to fill it.
     */
    public record Pose(float x, float y, float z, float yaw, float tilt, float width, float height) {}
}
