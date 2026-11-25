package justfatlard.pandorical.settings;

import justfatlard.pandorical.protocol.ModIconS2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The icon the server-drawn mods screen shows for each mod: the player's own texture for a mod
 * their game runs, else the server's copy, sent once a session to a client that can take it.
 */
final class ModIcons {
    private ModIcons() {}

    private static final Map<String, Optional<byte[]>> PNGS = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<String>> SENT = new ConcurrentHashMap<>();

    /** A texture id for the row, or empty to draw none. */
    static String textureFor(ServerPlayer player, String modId) {
        var mine = ClientMods.aboutOf(player, modId);
        if (mine != null) return mine.icon();
        if (!ServerPlayNetworking.canSend(player, ModIconS2C.TYPE)) return "";
        byte[] png = PNGS.computeIfAbsent(modId, id -> FabricLoader.getInstance().getModContainer(id)
            .map(ModCatalog::iconBytes)).orElse(null);
        if (png == null) return "";
        if (SENT.computeIfAbsent(player.getUUID(), uuid -> ConcurrentHashMap.newKeySet()).add(modId)) {
            ServerPlayNetworking.send(player, new ModIconS2C(modId, png));
        }
        return ModCatalog.ICON_PREFIX + modId;
    }

    static void forget(ServerPlayer player) {
        SENT.remove(player.getUUID());
    }
}
