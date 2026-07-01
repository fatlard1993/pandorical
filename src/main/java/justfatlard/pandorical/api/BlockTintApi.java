package justfatlard.pandorical.api;

/**
 * API for registering block tint (color) mappings.
 * Registrations are synced to connecting clients during the configuration phase.
 * Call from your mod's {@code onInitialize()}.
 */
public interface BlockTintApi {
    void grass(String... blockIds);
    void stem(String... blockIds);
    void sugarCane(String... blockIds);
    void foliage(String... blockIds);
    void constant(int argb, String... blockIds);
}
