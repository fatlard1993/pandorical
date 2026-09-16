package justfatlard.pandorical.client.maprelief;

import it.unimi.dsi.fastutil.floats.FloatArrayList;

/**
 * A volume of coloured voxels as the faces that show, merged into rectangles of one colour, so a
 * meadow is a handful of quads rather than one per block.
 *
 * <p>The volume is indexed {@code x + z * side + y * side * side}, y up. Quads come out in the
 * space a map is drawn in: x along the map, the volume's z down the map, and up out of the frame
 * towards negative z, each voxel {@code size} units across. The floor of the volume rests on the
 * frame and is never drawn.
 */
final class ReliefMesher {
    private ReliefMesher() {}

    /** A quad is its normal, its colour as float bits, then x, y, z at each of its four corners. */
    static final int QUAD = 3 + 1 + 4 * 3;

    /** Faces, for the colour of each: 0 the top of a block, 1 a side, 2 its underside. */
    static final int TOP = 0, SIDE = 1, BOTTOM = 2;

    /**
     * @param voxels colour keys, 0 for nothing
     * @param colour a face's ARGB colour by key and face; never 0
     */
    static float[] mesh(int side, int height, int size, int[] voxels, FaceColour colour) {
        FloatArrayList out = new FloatArrayList();
        int[] mask = new int[side * Math.max(side, height)];
        int[] dims = {side, height, side};
        int[] at = new int[3];
        // axis 0 is x, 1 is y (up), 2 is z; each face direction sweeps planes along its axis
        for (int axis = 0; axis < 3; axis++) {
            int u = axis == 0 ? 1 : 0, v = axis == 2 ? 1 : 2;
            int du = dims[u], dv = dims[v];
            for (int sign = -1; sign <= 1; sign += 2) {
                int face = axis != 1 ? SIDE : sign > 0 ? TOP : BOTTOM;
                for (int plane = 0; plane < dims[axis]; plane++) {
                    if (axis == 1 && sign < 0 && plane == 0) continue;
                    for (int j = 0; j < dv; j++) {
                        for (int i = 0; i < du; i++) {
                            at[axis] = plane;
                            at[u] = i;
                            at[v] = j;
                            int here = voxel(voxels, side, height, at[0], at[1], at[2]);
                            at[axis] = plane + sign;
                            mask[i + j * du] = here != 0 && voxel(voxels, side, height, at[0], at[1], at[2]) == 0
                                ? colour.of(here, face) : 0;
                        }
                    }
                    merge(out, mask, du, dv, axis, u, v, sign, sign > 0 ? plane + 1 : plane, size);
                }
            }
        }
        return out.toFloatArray();
    }

    /**
     * The same volume at {@code 1 / factor} the resolution, for drawing from further off: each
     * coarse voxel is the highest filled voxel in the cube it covers, so the ground keeps its top.
     *
     * @return the coarse voxels; the coarse side and height are the fine ones divided by
     *         {@code factor}, rounded up
     */
    static int[] coarsen(int side, int height, int[] voxels, int factor) {
        int cs = (side + factor - 1) / factor, ch = (height + factor - 1) / factor;
        int[] coarse = new int[cs * cs * ch];
        for (int cy = 0; cy < ch; cy++) {
            for (int cz = 0; cz < cs; cz++) {
                for (int cx = 0; cx < cs; cx++) {
                    int found = 0;
                    for (int y = Math.min(height, (cy + 1) * factor) - 1; y >= cy * factor && found == 0; y--) {
                        for (int z = cz * factor; z < Math.min(side, (cz + 1) * factor) && found == 0; z++) {
                            for (int x = cx * factor; x < Math.min(side, (cx + 1) * factor) && found == 0; x++) {
                                found = voxels[x + z * side + y * side * side];
                            }
                        }
                    }
                    coarse[cx + cz * cs + cy * cs * cs] = found;
                }
            }
        }
        return coarse;
    }

    private static void merge(FloatArrayList out, int[] mask, int du, int dv, int axis, int u, int v, int sign, int plane, int size) {
        for (int j = 0; j < dv; j++) {
            for (int i = 0; i < du; ) {
                int c = mask[i + j * du];
                if (c == 0) {
                    i++;
                    continue;
                }
                int w = 1;
                while (i + w < du && mask[i + w + j * du] == c) w++;
                int h = 1;
                grow:
                while (j + h < dv) {
                    for (int k = 0; k < w; k++) if (mask[i + k + (j + h) * du] != c) break grow;
                    h++;
                }
                for (int b = 0; b < h; b++) for (int a = 0; a < w; a++) mask[i + a + (j + b) * du] = 0;
                quad(out, axis, u, v, sign, plane, i, j, w, h, c, size);
                i += w;
            }
        }
    }

    private static void quad(FloatArrayList out, int axis, int u, int v, int sign, int plane, int i, int j, int w, int h, int c, int size) {
        float[][] corners = new float[4][];
        int[][] steps = {{0, 0}, {w, 0}, {w, h}, {0, h}};
        for (int k = 0; k < 4; k++) {
            float[] at = new float[3];
            at[axis] = plane;
            at[u] = i + steps[k][0];
            at[v] = j + steps[k][1];
            // volume (x, y, z) to map space: x, z down the map, y out of the frame towards -z
            corners[k] = new float[] {at[0] * size, at[2] * size, -at[1] * size};
        }
        float[] normal = new float[3];
        int mapAxis = axis == 0 ? 0 : axis == 2 ? 1 : 2;
        normal[mapAxis] = axis == 1 ? -sign : sign;
        // Wound counter-clockwise seen from outside, whichever way the steps happened to go round
        float[] e1 = sub(corners[1], corners[0]), e2 = sub(corners[2], corners[1]);
        float facing = (e1[1] * e2[2] - e1[2] * e2[1]) * normal[0] + (e1[2] * e2[0] - e1[0] * e2[2]) * normal[1]
            + (e1[0] * e2[1] - e1[1] * e2[0]) * normal[2];
        if (facing < 0) {
            float[] swap = corners[1];
            corners[1] = corners[3];
            corners[3] = swap;
        }
        out.add(normal[0]);
        out.add(normal[1]);
        out.add(normal[2]);
        out.add(Float.intBitsToFloat(c));
        for (float[] corner : corners) {
            out.add(corner[0]);
            out.add(corner[1]);
            out.add(corner[2]);
        }
    }

    private static float[] sub(float[] a, float[] b) {
        return new float[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static int voxel(int[] voxels, int side, int height, int x, int y, int z) {
        if (x < 0 || z < 0 || y < 0 || x >= side || z >= side || y >= height) return 0;
        return voxels[x + z * side + y * side * side];
    }

    @FunctionalInterface
    interface FaceColour {
        int of(int key, int face);
    }
}
