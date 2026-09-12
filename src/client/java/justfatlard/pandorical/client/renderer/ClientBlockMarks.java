package justfatlard.pandorical.client.renderer;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import justfatlard.pandorical.protocol.BlockMarksS2C;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public final class ClientBlockMarks {
    private ClientBlockMarks() {}

    private static final Map<Identifier, Map<Long, Set<String>>> marks = new ConcurrentHashMap<>();

    public static void apply(BlockMarksS2C payload) {
        Map<Long, Set<String>> level = marks.computeIfAbsent(payload.dimension(), k -> new ConcurrentHashMap<>());
        Minecraft client = Minecraft.getInstance();
        for (BlockMarksS2C.Entry entry : payload.entries()) {
            Set<String> at = level.computeIfAbsent(entry.pos(), k -> ConcurrentHashMap.newKeySet());
            if (entry.on()) at.add(entry.mark()); else at.remove(entry.mark());
            if (at.isEmpty()) level.remove(entry.pos());
            // The block did not change, but what is drawn for it did.
            if (client.level != null && client.level.dimension().identifier().equals(payload.dimension())) {
                BlockPos pos = BlockPos.of(entry.pos());
                client.level.setBlocksDirty(pos, client.level.getBlockState(pos), client.level.getBlockState(pos));
            }
        }
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
