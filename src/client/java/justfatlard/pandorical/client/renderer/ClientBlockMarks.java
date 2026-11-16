package justfatlard.pandorical.client.renderer;

import justfatlard.pandorical.protocol.BlockMarksS2C;
import justfatlard.pandorical.protocol.BlockMarksV3S2C;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Read from chunk builders as well as the client thread, so every level of it is concurrent. */
@Environment(EnvType.CLIENT)
public final class ClientBlockMarks {
    private ClientBlockMarks() {}

    /** World, then packed chunk, then packed position. */
    private static final Map<Identifier, Map<Long, Map<Long, Set<String>>>> marks = new ConcurrentHashMap<>();
    /** The server speaks chunk by chunk, and says a chunk's marks again whenever it sends the chunk. */
    private static volatile boolean byChunk;

    public static void apply(BlockMarksS2C payload) {
        apply(payload.dimension(), false, payload.entries());
    }

    /**
     * @param replace these are every mark in the world the player is now in: what was held for any
     *                world, this one included, is out of date, and is dropped first
     */
    public static void apply(Identifier dimension, boolean replace, List<BlockMarksS2C.Entry> entries) {
        Minecraft client = Minecraft.getInstance();
        if (replace) {
            Map<Long, Map<Long, Set<String>>> before = marks.get(dimension);
            marks.clear();
            if (before != null) dirtyAll(client, dimension, before);
        }
        add(client, dimension, entries);
    }

    /** One chunk's marks: all of them, dropping what was held there, or changes to them. */
    public static void apply(BlockMarksV3S2C payload) {
        byChunk = true;
        Minecraft client = Minecraft.getInstance();
        if (payload.replace()) {
            Map<Long, Map<Long, Set<String>>> level = marks.get(payload.dimension());
            Map<Long, Set<String>> before = level == null ? null : level.remove(payload.chunk());
            if (before != null) for (long pos : before.keySet()) dirty(client, payload.dimension(), pos);
        }
        add(client, payload.dimension(), payload.entries());
    }

    private static void add(Minecraft client, Identifier dimension, List<BlockMarksS2C.Entry> entries) {
        Map<Long, Map<Long, Set<String>>> level = marks.computeIfAbsent(dimension, k -> new ConcurrentHashMap<>());
        for (BlockMarksS2C.Entry entry : entries) {
            long chunk = ChunkPos.pack(BlockPos.of(entry.pos()));
            Map<Long, Set<String>> inChunk = level.computeIfAbsent(chunk, k -> new ConcurrentHashMap<>());
            Set<String> at = inChunk.computeIfAbsent(entry.pos(), k -> ConcurrentHashMap.newKeySet());
            if (entry.on()) at.add(entry.mark()); else at.remove(entry.mark());
            if (at.isEmpty()) inChunk.remove(entry.pos());
            if (inChunk.isEmpty()) level.remove(chunk);
            dirty(client, dimension, entry.pos());
        }
    }

    /** The chunk left this client: its marks come again with it, from a server that speaks by chunk. */
    public static void forgetChunk(Identifier dimension, long chunk) {
        if (!byChunk) return;
        Map<Long, Map<Long, Set<String>>> level = marks.get(dimension);
        if (level != null) level.remove(chunk);
    }

    /** The client changed world: every chunk comes again, with its marks, from a server that speaks by chunk. */
    public static void forgetWorlds() {
        if (byChunk) marks.clear();
    }

    private static void dirtyAll(Minecraft client, Identifier dimension, Map<Long, Map<Long, Set<String>>> level) {
        for (Map<Long, Set<String>> inChunk : level.values()) {
            for (long pos : inChunk.keySet()) dirty(client, dimension, pos);
        }
    }

    /** The block did not change, but what is drawn for it did. */
    private static void dirty(Minecraft client, Identifier dimension, long at) {
        if (client.level == null || !client.level.dimension().identifier().equals(dimension)) return;
        BlockPos pos = BlockPos.of(at);
        client.level.setBlocksDirty(pos, client.level.getBlockState(pos), client.level.getBlockState(pos));
    }

    public static void clear() {
        marks.clear();
        byChunk = false;
    }

    private static Set<String> marksAt(BlockPos pos) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return null;
        Map<Long, Map<Long, Set<String>>> level = marks.get(client.level.dimension().identifier());
        Map<Long, Set<String>> inChunk = level == null ? null : level.get(ChunkPos.pack(pos));
        return inChunk == null ? null : inChunk.get(pos.asLong());
    }

    public static boolean has(BlockPos pos, String mark) {
        Set<String> at = marksAt(pos);
        return at != null && at.contains(mark);
    }

    public static List<String> at(BlockPos pos) {
        Set<String> at = marksAt(pos);
        return at == null ? List.of() : List.copyOf(at);
    }
}
