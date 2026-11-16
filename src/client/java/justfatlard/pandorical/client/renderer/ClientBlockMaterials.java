package justfatlard.pandorical.client.renderer;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import justfatlard.pandorical.protocol.BlockMaterialsS2C;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * The materials the server has set by position: what a block there sounds and crumbles like.
 * Asked on every step, hit and break, so the first question is one flag: whether there are any.
 *
 * <p>Nothing here forgets a position on its own. The breaker's client plays a block's break
 * before the server has heard of it, and the server's clear arrives after, so the material is
 * still here for the break.
 */
@Environment(EnvType.CLIENT)
public final class ClientBlockMaterials {
    private ClientBlockMaterials() {}

    private static final Map<Identifier, Long2ObjectOpenHashMap<BlockState>> materials = new HashMap<>();
    /** Whether any material is set anywhere; read without a lock on every lookup. */
    public static volatile boolean any;

    public static void apply(BlockMaterialsS2C payload) {
        // The server only ever speaks of the world the player is in: what it held for any world,
        // this one included, is out of date.
        if (payload.replace()) materials.clear();
        Long2ObjectOpenHashMap<BlockState> level = materials.computeIfAbsent(payload.dimension(), k -> new Long2ObjectOpenHashMap<>());
        for (BlockMaterialsS2C.Entry entry : payload.entries()) {
            if (entry.material().isAir()) level.remove(entry.pos());
            else level.put(entry.pos(), entry.material());
        }
        any = materials.values().stream().anyMatch(m -> !m.isEmpty());
    }

    public static void clear() {
        materials.clear();
        any = false;
    }

    /** The material set for this position in the world the client is in, or null; only on the client's own thread. */
    public static BlockState at(BlockPos pos) {
        if (!any) return null;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || !client.isSameThread()) return null;
        Long2ObjectOpenHashMap<BlockState> level = materials.get(client.level.dimension().identifier());
        return level == null ? null : level.get(pos.asLong());
    }

    /** The state to sound and crumble as at this position: its material if one is set, else the state itself. */
    public static BlockState as(BlockState state, BlockPos pos) {
        if (!any || state.isAir()) return state;
        BlockState material = at(pos);
        return material == null ? state : material;
    }

    /** The sounds a block at this position makes: its material's if one is set, else its own. */
    public static SoundType soundType(BlockState state, BlockPos pos) {
        return as(state, pos).getSoundType();
    }
}
