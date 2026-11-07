package justfatlard.pandorical.client.renderer;

import justfatlard.pandorical.protocol.BlockMarksS2C;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Environment(EnvType.CLIENT)
public final class ClientBlockMarks {
    private ClientBlockMarks() {}

    private static final Map<Identifier, Map<Long, Set<String>>> marks = new ConcurrentHashMap<>();

    public static void apply(BlockMarksS2C payload) {
        apply(payload.dimension(), false, payload.entries());
    }

    /**
     * @param replace these are every mark in the world the player is now in: what was held for any
     *                world, this one included, is out of date, and is dropped first
     */
    public static void apply(Identifier dimension, boolean replace, java.util.List<BlockMarksS2C.Entry> entries) {
        Minecraft client = Minecraft.getInstance();
        if (replace) {
            Map<Long, Set<String>> before = marks.get(dimension);
            marks.clear();
            if (before != null) for (long pos : before.keySet()) dirty(client, dimension, pos);
        }
        Map<Long, Set<String>> level = marks.computeIfAbsent(dimension, k -> new ConcurrentHashMap<>());
        for (BlockMarksS2C.Entry entry : entries) {
            Set<String> at = level.computeIfAbsent(entry.pos(), k -> ConcurrentHashMap.newKeySet());
            if (entry.on()) at.add(entry.mark()); else at.remove(entry.mark());
            if (at.isEmpty()) level.remove(entry.pos());
            dirty(client, dimension, entry.pos());
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
    }

    public static boolean has(BlockPos pos, String mark) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return false;
        Map<Long, Set<String>> level = marks.get(client.level.dimension().identifier());
        if (level == null) return false;
        Set<String> at = level.get(pos.asLong());
        return at != null && at.contains(mark);
    }

    public static List<String> at(BlockPos pos) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return List.of();
        Map<Long, Set<String>> level = marks.get(client.level.dimension().identifier());
        Set<String> at = level == null ? null : level.get(pos.asLong());
        return at == null ? List.of() : List.copyOf(at);
    }
}
