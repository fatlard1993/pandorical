package justfatlard.pandorical.api;

/**
 * Dropped items and XP orbs, made cheaper to have lying about.
 *
 * <p>Three fixes, each off unless a mod asks for it, and each one ops can turn from Pandorical's
 * page of the mod menu, which outranks what any mod asked for. All three are the server's alone:
 * a vanilla client sees the same world, with bigger orbs.
 *
 * <p>Clumps and Get It Together, Drops! do the first two their own way; with either installed,
 * its fix here stays off whatever anyone asked for.
 */
public interface DropsApi {
    /**
     * Whether XP orbs of any value merge into one, and one touch takes a whole orb, when no op has
     * chosen. The player ends up with the XP and the Mending repair vanilla would give, at once.
     * Off until a mod asks. The last call wins among mods, and an op's choice outranks them all.
     */
    void clumpExperience(boolean byDefault);

    /**
     * How far apart two dropped stacks of one item may be and still merge, when no op has chosen:
     * the gap between their boxes, sideways, in blocks, to the nearest tenth and at most 4.
     * Vanilla's is 0.5, which leaves the fix off. With it on, stacks never merge through a block,
     * and never past a full stack. The last call wins among mods, and an op's choice outranks them
     * all.
     */
    void itemMergeRadius(double blocks);

    /**
     * How far from a player, in blocks, dropped items and XP orbs are sent to them, when no op has
     * chosen: lower than vanilla's 96, to spare clients a pile too far off to matter. 0 is
     * vanilla's. A change reaches the items and orbs tracked after it; those already tracked keep
     * the range they had. The last call wins among mods, and an op's choice outranks them all.
     */
    void itemTrackingRange(int blocks);
}
