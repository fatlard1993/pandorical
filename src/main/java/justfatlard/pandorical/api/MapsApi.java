package justfatlard.pandorical.api;

import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * Whether a player wants to be shown on maps, the minimap, the web map and any other: their position
 * and their home. Each player's own choice, on Pandorical's page of their settings; on until they turn
 * it off. A map should also leave off spectators and invisible players, which this does not decide.
 */
public interface MapsApi {
    /** Works for a player who is offline, which a map of homes needs. */
    boolean shown(MinecraftServer server, UUID player);
}
