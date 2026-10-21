package justfatlard.pandorical.settings;

import com.mojang.brigadier.CommandDispatcher;
import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.api.ClientKind;
import justfatlard.pandorical.api.NoticeApi;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.entitykind.EntityKinds;
import justfatlard.pandorical.login.ClientKinds;
import justfatlard.pandorical.login.ServedClientMods;
import justfatlard.pandorical.protocol.ClientModFilesC2S;
import justfatlard.pandorical.protocol.InstallKeptS2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
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

    private static final String UPDATE_KIND = "pandorical:update";

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
        Pandorical.LOGGER.info("[pandorical] {}: {}", player.getName().getString(), withoutPandorical(player));
    }

    private static String withoutPandorical(ServerPlayer player) {
        return ClientKinds.of(player.getUUID()) == ClientKind.BEDROCK ? "Bedrock, through Geyser" : "no Pandorical";
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
     * The notice's own answers. Install sends the client the word to ask now; a client before
     * 15.24.1 cannot take it, so it is offered only a dismissal and told where the question waits.
     */
    public static void registerNotice() {
        PandoricalApi.notices().onChoice(UPDATE_KIND, (player, noticeId, choiceId) -> {
            if ("install".equals(choiceId) && ServerPlayNetworking.canSend(player, InstallKeptS2C.TYPE)) {
                ServerPlayNetworking.send(player, new InstallKeptS2C());
            }
        });
    }

    /**
     * A game running an older mod than this server keeps for it has been sent the new one, and
     * plays on the old one until it installs it and restarts. The client asks at the next menu it
     * shows, which a player who joined straight into the world may not open for an hour, and a
     * chat line on joining scrolled away under everybody else's arrival. So the server leaves a
     * notice, once a join, which waits in the tray until it is answered.
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
        String newer = "Newer mods for your game: " + String.join(", ", behind);
        boolean canInstall = ServerPlayNetworking.canSend(player, InstallKeptS2C.TYPE);
        PandoricalApi.notices().offer(player, new NoticeApi.Notice("update", UPDATE_KIND, "minecraft:emerald",
            canInstall ? newer : newer + ". Press Esc and pick Install and quit, then start the game again.",
            canInstall
                ? List.of(new NoticeApi.Choice("install", "minecraft:lime_dye", "Install now"),
                    new NoticeApi.Choice("later", "minecraft:clock", "Later"))
                : List.of(),
            0));
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
                line = withoutPandorical(player);
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
