package justfatlard.pandorical.settings;

import com.mojang.brigadier.CommandDispatcher;
import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.entitykind.EntityKinds;
import justfatlard.pandorical.login.ServedClientMods;
import justfatlard.pandorical.protocol.ClientModFilesC2S;
import net.minecraft.commands.CommandSourceStack;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /** Players already told this session that their game is behind. */
    private static final Set<UUID> TOLD = ConcurrentHashMap.newKeySet();

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
            protocol, EntityKinds.understandsBodies(player) ? "draws mod entities"
            : EntityKinds.understands(player) ? "draws mod entities on a vanilla base only"
            : "shown mod entities as their vanilla fallback");
    }

    /** At joining, for a game that never registered Pandorical's channels at all. */
    public static void vanilla(ServerPlayer player) {
        Pandorical.LOGGER.info("[pandorical] {}: no Pandorical on this client", player.getName().getString());
    }

    /** When the client reports its mods, on joining and after every change it makes to them. */
    public static void reported(ServerPlayer player, List<ClientModFilesC2S.Entry> entries) {
        tellIfBehind(player, entries);
        String line = summary(entries);
        if (line.equals(LOGGED.put(player.getUUID(), line))) return;
        Pandorical.LOGGER.info("[pandorical] {} runs {}", player.getName().getString(), line);
    }

    public static void forget(UUID player) {
        LOGGED.remove(player);
        TOLD.remove(player);
    }

    /**
     * A game running an older mod than this server keeps for it has been sent the new one, and
     * plays on the old one until it installs it and restarts. Clients before 15.21 installed it and
     * played on without a word about restarting, and a client cannot be taught that after the
     * fact, so the server says it: once a join, to the player whose game it is.
     */
    private static void tellIfBehind(ServerPlayer player, List<ClientModFilesC2S.Entry> entries) {
        Map<String, String> kept = ServedClientMods.versions();
        List<String> behind = new ArrayList<>();
        for (ClientModFilesC2S.Entry entry : entries) {
            String offered = kept.get(entry.id());
            if (entry.enabled() && offered != null && newer(offered, entry.version())) {
                behind.add(entry.name() + " " + offered + " (you are running " + entry.version() + ")");
            }
        }
        if (behind.isEmpty() || !TOLD.add(player.getUUID())) return;
        player.sendSystemMessage(Component.literal("This server has newer mods for your game: "
            + String.join(", ", behind) + ". Open the pause menu to install, then restart the game.")
            .withStyle(ChatFormatting.YELLOW));
    }

    private static boolean newer(String offered, String running) {
        try {
            return Version.parse(offered).compareTo(Version.parse(running)) > 0;
        } catch (VersionParsingException e) {
            return false;
        }
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
