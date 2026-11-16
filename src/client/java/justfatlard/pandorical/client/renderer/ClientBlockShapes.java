package justfatlard.pandorical.client.renderer;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import justfatlard.pandorical.protocol.BlockShapesS2C;
import justfatlard.pandorical.protocol.BlockShapesV2S2C;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The shapes the server has set by position, built once into shapes as they arrive. Asked from
 * the hottest calls in the game, a block's shape and its collision, so the first question is one
 * flag: whether there are any at all.
 */
@Environment(EnvType.CLIENT)
public final class ClientBlockShapes {
    private ClientBlockShapes() {}

    /** World, then packed chunk, then packed position; never an empty chunk, so a world held is a shape held. */
    private static final Map<Identifier, Long2ObjectOpenHashMap<Long2ObjectOpenHashMap<VoxelShape>>> shapes = new HashMap<>();
    /** Whether any shape is set anywhere; read without a lock on every shape asked for. */
    public static volatile boolean any;
    /** The server speaks chunk by chunk, and says a chunk's shapes again whenever it sends the chunk. */
    private static boolean byChunk;

    public static void apply(BlockShapesS2C payload) {
        Minecraft client = Minecraft.getInstance();
        if (payload.replace()) {
            // The server only ever speaks of the world the player is in: what it held for any
            // world, this one included, is out of date, and its old positions' outlines with it.
            var before = shapes.get(payload.dimension());
            shapes.clear();
            if (before != null) for (var inChunk : before.values()) for (long pos : inChunk.keySet()) dirty(client, payload.dimension(), pos);
        }
        add(client, payload.dimension(), payload.entries());
    }

    /** One chunk's shapes: all of them, dropping what was held there, or changes to them. */
    public static void apply(BlockShapesV2S2C payload) {
        byChunk = true;
        Minecraft client = Minecraft.getInstance();
        if (payload.replace()) {
            var level = shapes.get(payload.dimension());
            var before = level == null ? null : level.remove(payload.chunk());
            if (before != null) for (long pos : before.keySet()) dirty(client, payload.dimension(), pos);
        }
        add(client, payload.dimension(), payload.entries());
    }

    private static void add(Minecraft client, Identifier dimension, List<BlockShapesS2C.Entry> entries) {
        var level = shapes.computeIfAbsent(dimension, k -> new Long2ObjectOpenHashMap<>());
        for (BlockShapesS2C.Entry entry : entries) {
            long chunk = ChunkPos.pack(BlockPos.of(entry.pos()));
            if (entry.boxes().isEmpty()) {
                var inChunk = level.get(chunk);
                if (inChunk != null) {
                    inChunk.remove(entry.pos());
                    if (inChunk.isEmpty()) level.remove(chunk);
                }
            } else {
                VoxelShape shape = Shapes.empty();
                for (BlockShapesS2C.Box box : entry.boxes()) {
                    shape = Shapes.joinUnoptimized(shape, Shapes.box(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()),
                        net.minecraft.world.phys.shapes.BooleanOp.OR);
                }
                level.computeIfAbsent(chunk, k -> new Long2ObjectOpenHashMap<>()).put(entry.pos(), shape.optimize());
            }
            dirty(client, dimension, entry.pos());
        }
        settle();
    }

    /** The chunk left this client: its shapes come again with it, from a server that speaks by chunk. */
    public static void forgetChunk(Identifier dimension, long chunk) {
        if (!byChunk) return;
        var level = shapes.get(dimension);
        if (level != null && level.remove(chunk) != null) settle();
    }

    /** The client changed world: every chunk comes again, with its shapes, from a server that speaks by chunk. */
    public static void forgetWorlds() {
        if (!byChunk) return;
        shapes.clear();
        any = false;
    }

    private static void settle() {
        shapes.values().removeIf(Map::isEmpty);
        any = !shapes.isEmpty();
    }

    /** Its outline changed, though the block did not. */
    private static void dirty(Minecraft client, Identifier dimension, long at) {
        if (client.level == null || !client.level.dimension().identifier().equals(dimension)) return;
        BlockPos pos = BlockPos.of(at);
        client.level.setBlocksDirty(pos, client.level.getBlockState(pos), client.level.getBlockState(pos));
    }

    public static void clear() {
        shapes.clear();
        any = false;
        byChunk = false;
    }

    /** The shape set for this position in the world the client is in, or null; only on the client's own thread. */
    public static VoxelShape at(BlockPos pos) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || !client.isSameThread()) return null;
        var level = shapes.get(client.level.dimension().identifier());
        var inChunk = level == null ? null : level.get(ChunkPos.pack(pos));
        return inChunk == null ? null : inChunk.get(pos.asLong());
    }
}
