package justfatlard.pandorical.client;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.protocol.NotUnderstoodC2S;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Tells the server about a word this client could not act on, and says it once.
 *
 * <p>The server author cannot see this client's log, so a feature that quietly does not appear is
 * a bug nobody can find. Reporting it back puts the news where the mod is written.
 */
public final class ClientNotices {
    private ClientNotices() {}

    private static final Set<String> said = ConcurrentHashMap.newKeySet();

    /** Content arrives during configuration, where a play packet cannot be sent yet. */
    private static final Queue<NotUnderstoodC2S> waiting = new ConcurrentLinkedQueue<>();

    /** Enough to show a pattern without answering a badly behaved server packet for packet. */
    private static final int PER_SESSION = 64;

    /**
     * @param kind  one of {@link justfatlard.pandorical.api.NotUnderstood}'s kinds
     * @param value the word as the server said it
     */
    public static void report(String kind, String value) {
        if (value == null || value.isEmpty()) return;
        // Deduped before it is logged: some of these sit in the render loop.
        if (said.size() >= PER_SESSION || !said.add(kind + '\u0000' + value)) return;
        // Logged as well as sent: on a vanilla server, or one too old to hear it, this is all there is.
        Pandorical.LOGGER.warn("Unknown {} '{}': that feature is absent. The server is newer than this"
            + " client, or the value is wrong.", kind, value);
        send(new NotUnderstoodC2S(kind, value));
    }

    private static void send(NotUnderstoodC2S notice) {
        // No play connection yet means configuration, where a play packet cannot go.
        if (Minecraft.getInstance().getConnection() == null) {
            waiting.add(notice);
            return;
        }
        if (!ClientPlayNetworking.canSend(NotUnderstoodC2S.TYPE)) return;
        ClientPlayNetworking.send(notice);
    }

    /** Sent once the play phase is up, for anything noticed while content was still arriving. */
    public static void flush() {
        if (waiting.isEmpty() || Minecraft.getInstance().getConnection() == null) return;
        // Asked before the queue is emptied. Clearing first threw the reports away on any server
        // without the channel, and every word had already been marked as said, so they were never
        // raised again.
        if (!ClientPlayNetworking.canSend(NotUnderstoodC2S.TYPE)) return;
        List<NotUnderstoodC2S> pending = List.copyOf(waiting);
        waiting.clear();
        pending.forEach(ClientPlayNetworking::send);
    }

    /** The next server has its own vocabulary, and has not been told anything yet. */
    public static void forgetConnection() {
        said.clear();
        waiting.clear();
    }
}
