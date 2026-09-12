package justfatlard.pandorical.api;

import java.util.Objects;

/**
 * A grid of palette-coloured cells on a thin panel.
 *
 * <p>Cells are palette indices, row-major from the top left, as a {@link PixelCanvas} holds
 * them. The front shows the cells; the back and edges are {@code backColor}, not drawn at all
 * when its alpha is 0.
 *
 * @param palette   ARGB colours, indexed by cell value
 * @param thickness of the panel, in blocks
 */
public record Picture(int columns, int rows, int[] palette, byte[] cells, Pose pose, int backColor, float thickness) {
    /** Most cells to a side: every client in sight of a picture holds a texture this size for it. */
    public static final int LARGEST_SIDE = 512;

    /**
     * @throws IllegalArgumentException if a side is under 1 or over {@link #LARGEST_SIDE}, or
     *                                  {@code cells} does not hold exactly {@code columns * rows}
     */
    public Picture {
        Objects.requireNonNull(palette, "palette");
        Objects.requireNonNull(cells, "cells");
        Objects.requireNonNull(pose, "pose");
        if (columns < 1 || rows < 1 || columns > LARGEST_SIDE || rows > LARGEST_SIDE) {
            throw new IllegalArgumentException("picture size " + columns + "x" + rows + " is outside 1.." + LARGEST_SIDE);
        }
        if (cells.length != columns * rows) {
            throw new IllegalArgumentException(cells.length + " cells for a " + columns + "x" + rows + " picture");
        }
    }

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
