package justfatlard.pandorical.api;

/**
 * An integer position relative to a structure's origin (its anchor entity's world position
 * at the time of the current {@link StructurePose}). Used as the key/offset for every block
 * in a structure: {@code (0,0,0)} sits at the structure's origin, unrotated.
 */
public record RelPos(int x, int y, int z) {
}
