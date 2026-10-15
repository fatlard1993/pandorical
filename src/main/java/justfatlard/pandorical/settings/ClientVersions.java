package justfatlard.pandorical.settings;

import com.mojang.brigadier.CommandDispatcher;
import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.entitykind.EntityKinds;
import justfatlard.pandorical.protocol.ClientModFilesC2S;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What each player's game is actually running, written to the server log and answerable in game.
 *
 * <p>Most "it looks wrong for me" reports come down to a client older than the server: a
 * Pandorical that downloaded the new version on joining but has not been restarted into it, a
 * Fabric API left behind. The client already reports its loaded mods by their own metadata, which
 * is what is running rather than what is sitting in the folder; this says it out loud.
 */
public final class ClientVersions {
    private ClientVersions() {}

    /** The last line logged for each player, so a report that changed nothing is not logged twice. */
    private static final Map<UUID, String> LOGGED = new ConcurrentHashMap<>();

    /** Named first, in this order; everything else after them by id. */
    private static final List<String> FIRST = List.of("pandorical", "fabric-api", "minecraft", "fabricloader");

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        PandoricalApi.commandHelp().describe("/pandorical clients",
            "Which Pandorical and mods each player's game is running. Ops only.");
        dispatcher.register(Commands.literal("pandorical")
            .then(Commands.literal("clients")
                .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                .executes(context -> list(context.getSource()))));
    }

    /** At the handshake: every Pandorical client sends one, however old. */
    public static void hello(ServerPlayer player, int protocol) {
        Pandorical.LOGGER.info("[pandorical] {}: Pandorical protocol v{}, {}", player.getName().getString(),
            protocol, EntityKinds.understands(player) ? "draws mod entities" : "shown mod entities as their vanilla base");
    }

    /** At joining, for a game that never registered Pandorical's channels at all. */
    public static void vanilla(ServerPlayer player) {
        Pandorical.LOGGER.info("[pandorical] {}: no Pandorical on this client", player.getName().getString());
    }

    /** When the client reports its mods, on joining and after every change it makes to them. */
    public static void reported(ServerPlayer player, List<ClientModFilesC2S.Entry> entries) {
        String line = summary(entries);
        if (line.equals(LOGGED.put(player.getUUID(), line))) return;
        Pandorical.LOGGER.info("[pandorical] {} runs {}", player.getName().getString(), line);
    }

    public static void forget(UUID player) {
        LOGGED.remove(player);
    }

    /**
     * The headline versions, then every other mod, then what is switched off. Fabric API's own
     * modules are left out: they all carry its version, and fifty of them bury the rest.
     */
    static String summary(List<ClientModFilesC2S.Entry> entries) {
        List<String> head = new ArrayList<>();
        for (String id : FIRST) {
            entries.stream().filter(e -> e.enabled() && e.id().equals(id)).findFirst()
                .ifPresent(e -> head.add(e.id() + " " + e.version()));
        }
        List<String> rest = entries.stream()
            .filter(e -> e.enabled() && !FIRST.contains(e.id()) && !plumbing(e.id()))
            .sorted(Comparator.comparing(ClientModFilesC2S.Entry::id))
            .map(e -> e.id() + " " + e.version())
            .toList();
        List<String> off = entries.stream().filter(e -> !e.enabled())
            .map(e -> e.id() + (e.version().isEmpty() ? "" : " " + e.version()))
            .sorted().toList();

        StringBuilder out = new StringBuilder(String.join(", ", head));
        if (!rest.isEmpty()) out.append("; mods: ").append(String.join(", ", rest));
        if (!off.isEmpty()) out.append("; switched off: ").append(String.join(", ", off));
        // A development client loads its mods from folders, not jars, and reports none of them.
        return out.isEmpty() ? "nothing it could name (a development client?)" : out.toString();
    }

    private static boolean plumbing(String id) {
        return id.startsWith("fabric-") || id.equals("java") || id.equals("mixinextras");
    }

    private static int list(CommandSourceStack source) {
        var players = source.getServer().getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            String line;
            if (!PandoricalApi.isAvailable(player)) {
                line = "no Pandorical";
            } else {
                List<ClientModFilesC2S.Entry> files = ClientMods.filesOf(player);
                line = files.isEmpty() ? "Pandorical, too old to report its version" : summary(files);
            }
            String said = player.getName().getString() + ": " + line;
            source.sendSuccess(() -> Component.literal(said), false);
        }
        return players.size();
    }
}
