package justfatlard.pandorical.api;

/**
 * The world-space position and yaw rotation of a structure's origin.
 * Pitch/roll are intentionally omitted from this first version: no known consumer
 * (e.g. ships) needs anything beyond yaw, and adding them later is backward compatible
 * (a new packet/record can be introduced without breaking {@link #yaw}-only callers).
 */
public record StructurePose(double x, double y, double z, float yaw) {
}
