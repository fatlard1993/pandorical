package justfatlard.pandorical.client.renderer;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import justfatlard.pandorical.protocol.BlockShapesS2C;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;

/**
 * The shapes the server has set by position, built once into shapes as they arrive. Asked from
 * the hottest calls in the game, a block's shape and its collision, so the first question is one
 * flag: whether there are any at all.
 */
@Environment(EnvType.CLIENT)
public final class ClientBlockShapes {
    private ClientBlockShapes() {}

    private static final Map<Identifier, Long2ObjectOpenHashMap<VoxelShape>> shapes = new HashMap<>();
    /** Whether any shape is set anywhere; read without a lock on every shape asked for. */
    public static volatile boolean any;

    public static void apply(BlockShapesS2C payload) {
        Minecraft client = Minecraft.getInstance();
        if (payload.replace()) {
            // The server only ever speaks of the world the player is in: what it held for any
            // world, this one included, is out of date, and its old positions' outlines with it.
            Long2ObjectOpenHashMap<VoxelShape> before = shapes.get(payload.dimension());
            shapes.clear();
            if (before != null) for (long pos : before.keySet()) dirty(client, payload, pos);
        }
        Long2ObjectOpenHashMap<VoxelShape> level = shapes.computeIfAbsent(payload.dimension(), k -> new Long2ObjectOpenHashMap<>());
        for (BlockShapesS2C.Entry entry : payload.entries()) {
            if (entry.boxes().isEmpty()) {
                level.remove(entry.pos());
            } else {
                VoxelShape shape = Shapes.empty();
                for (BlockShapesS2C.Box box : entry.boxes()) {
                    shape = Shapes.joinUnoptimized(shape, Shapes.box(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()),
                        net.minecraft.world.phys.shapes.BooleanOp.OR);
                }
                level.put(entry.pos(), shape.optimize());
            }
            dirty(client, payload, entry.pos());
        }
        any = shapes.values().stream().anyMatch(m -> !m.isEmpty());
    }

    /** Its outline changed, though the block did not. */
    private static void dirty(Minecraft client, BlockShapesS2C payload, long at) {
        if (client.level == null || !client.level.dimension().identifier().equals(payload.dimension())) return;
        BlockPos pos = BlockPos.of(at);
        client.level.setBlocksDirty(pos, client.level.getBlockState(pos), client.level.getBlockState(pos));
    }

    public static void clear() {
        shapes.clear();
        any = false;
    }

    /** The shape set for this position in the world the client is in, or null; only on the client's own thread. */
    public static VoxelShape at(BlockPos pos) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || !client.isSameThread()) return null;
        Long2ObjectOpenHashMap<VoxelShape> level = shapes.get(client.level.dimension().identifier());
        return level == null ? null : level.get(pos.asLong());
    }
}
