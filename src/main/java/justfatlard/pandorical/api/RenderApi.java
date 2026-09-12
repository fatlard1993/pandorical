package justfatlard.pandorical.api;

/**
 * Server-wide rendering policies for Pandorical clients.
 *
 * <p>Declare at mod init: each client is told as it arrives, not when a policy changes, and drops
 * the policy when it leaves. Additive only: a policy can switch on what a client left off, never
 * switch anything off.
 */
public interface RenderApi {
    /** Ask clients to skip leaf faces buried inside a canopy; the outer layer still renders. */
    void cullLeaves(boolean enforce);
}
