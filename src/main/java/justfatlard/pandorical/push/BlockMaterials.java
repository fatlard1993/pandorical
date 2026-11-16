package justfatlard.pandorical.push;

import justfatlard.pandorical.api.BlockMaterialApi;
import justfatlard.pandorical.api.Capabilities;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.protocol.BlockMaterialsS2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Materials by position, per world, told to everybody in that world and to anybody who arrives. */
public final class BlockMaterials implements BlockMaterialApi {
    public static final BlockMaterials INSTANCE = new BlockMaterials();

    private final Map<ResourceKey<Level>, Map<Long, BlockState>> materials = new ConcurrentHashMap<>();

    private BlockMaterials() {}

    @Override
    public void setMaterial(ServerLevel level, BlockPos pos, BlockState material) {
        if (material.isAir()) throw new IllegalArgumentException("air is no material; clear it instead");
        BlockState before = materials.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>()).put(pos.asLong(), material);
        if (before == material) return;
        tell(level, List.of(new BlockMaterialsS2C.Entry(pos.asLong(), material)));
    }

    @Override
    public void clearMaterial(ServerLevel level, BlockPos pos) {
        Map<Long, BlockState> inLevel = materials.get(level.dimension());
        if (inLevel == null || inLevel.remove(pos.asLong()) == null) return;
        tell(level, List.of(new BlockMaterialsS2C.Entry(pos.asLong(), Blocks.AIR.defaultBlockState())));
    }

    /** @hidden */
    public void clear() {
        materials.clear();
    }

    /** The whole set for the world this player is now in, replacing whatever their client held: sent even when empty. */
    public void sendAll(ServerPlayer player) {
        Map<Long, BlockState> inLevel = materials.get(player.level().dimension());
        List<BlockMaterialsS2C.Entry> entries = new ArrayList<>();
        if (inLevel != null) inLevel.forEach((pos, material) -> entries.add(new BlockMaterialsS2C.Entry(pos, material)));
        send(player, player.level(), true, entries);
    }

    private void tell(ServerLevel level, List<BlockMaterialsS2C.Entry> entries) {
        for (ServerPlayer player : level.players()) send(player, level, false, entries);
    }

    private void send(ServerPlayer player, ServerLevel level, boolean replace, List<BlockMaterialsS2C.Entry> entries) {
        if (!PandoricalApi.hasCapability(player, Capabilities.BLOCK_MATERIALS) || !ServerPlayNetworking.canSend(player, BlockMaterialsS2C.TYPE)) return;
        ServerPlayNetworking.send(player, new BlockMaterialsS2C(level.dimension().identifier(), replace, entries));
    }
}
