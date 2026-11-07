package justfatlard.pandorical.push;

import justfatlard.pandorical.api.BlockShapeApi;
import justfatlard.pandorical.api.Capabilities;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.protocol.BlockShapesS2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Shapes by position, per world, told to everybody in that world and to anybody who arrives. */
public final class BlockShapes implements BlockShapeApi {
	public static final BlockShapes INSTANCE = new BlockShapes();

	private final Map<ResourceKey<Level>, Map<Long, List<BlockShapesS2C.Box>>> shapes = new ConcurrentHashMap<>();

	private BlockShapes() {}

	@Override
	public void setShape(ServerLevel level, BlockPos pos, List<AABB> boxes) {
		check(boxes);
		List<BlockShapesS2C.Box> wire = new ArrayList<>();
		for (AABB box : boxes) {
			wire.add(new BlockShapesS2C.Box((float) box.minX, (float) box.minY, (float) box.minZ,
				(float) box.maxX, (float) box.maxY, (float) box.maxZ));
		}
		shapes.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>()).put(pos.asLong(), List.copyOf(wire));
		tell(level, List.of(new BlockShapesS2C.Entry(pos.asLong(), wire)));
	}

	private static void check(List<AABB> boxes) {
		if (boxes.isEmpty()) throw new IllegalArgumentException("a shape needs at least one box; clear it instead");
	}

	@Override
	public void clearShape(ServerLevel level, BlockPos pos) {
		Map<Long, List<BlockShapesS2C.Box>> inLevel = shapes.get(level.dimension());
		if (inLevel == null || inLevel.remove(pos.asLong()) == null) return;
		tell(level, List.of(new BlockShapesS2C.Entry(pos.asLong(), List.of())));
	}

	/** @hidden */
	public void clear() {
		shapes.clear();
	}

	/** The whole set for the world this player is now in, replacing whatever their client held: sent even when empty. */
	public void sendAll(ServerPlayer player) {
		Map<Long, List<BlockShapesS2C.Box>> inLevel = shapes.get(player.level().dimension());
		List<BlockShapesS2C.Entry> entries = new ArrayList<>();
		if (inLevel != null) inLevel.forEach((pos, boxes) -> entries.add(new BlockShapesS2C.Entry(pos, boxes)));
		send(player, player.level(), true, entries);
	}

	private void tell(ServerLevel level, List<BlockShapesS2C.Entry> entries) {
		for (ServerPlayer player : level.players()) send(player, level, false, entries);
	}

	private void send(ServerPlayer player, ServerLevel level, boolean replace, List<BlockShapesS2C.Entry> entries) {
		// The capability, not just the channel: it is what the server block asks before giving a
		// player the shape, and the two must agree or the player rubber-bands.
		if (!PandoricalApi.hasCapability(player, Capabilities.BLOCK_SHAPES) || !ServerPlayNetworking.canSend(player, BlockShapesS2C.TYPE)) return;
		ServerPlayNetworking.send(player, new BlockShapesS2C(level.dimension().identifier(), replace, entries));
	}
}
