package justfatlard.pandorical.push;

import justfatlard.pandorical.api.BlockShapeApi;
import justfatlard.pandorical.api.Capabilities;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.protocol.BlockShapesS2C;
import justfatlard.pandorical.protocol.BlockShapesV2S2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shapes by position, per world and chunk. A client with the chunk channel is told a chunk's
 * shapes as the chunk is sent to it, and changes only while it has the chunk; one without is told
 * the whole world as it arrives in it, and every change in that world.
 */
public final class BlockShapes implements BlockShapeApi {
	public static final BlockShapes INSTANCE = new BlockShapes();

	/** World, then packed chunk, then packed position. */
	private final Map<ResourceKey<Level>, Map<Long, Map<Long, List<BlockShapesS2C.Box>>>> shapes = new ConcurrentHashMap<>();

	private BlockShapes() {}

	@Override
	public void setShape(ServerLevel level, BlockPos pos, List<AABB> boxes) {
		check(boxes);
		List<BlockShapesS2C.Box> wire = new ArrayList<>();
		for (AABB box : boxes) {
			wire.add(new BlockShapesS2C.Box((float) box.minX, (float) box.minY, (float) box.minZ,
				(float) box.maxX, (float) box.maxY, (float) box.maxZ));
		}
		long chunk = ChunkPos.pack(pos);
		shapes.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>())
			.computeIfAbsent(chunk, k -> new ConcurrentHashMap<>())
			.put(pos.asLong(), List.copyOf(wire));
		tell(level, chunk, List.of(new BlockShapesS2C.Entry(pos.asLong(), wire)));
	}

	private static void check(List<AABB> boxes) {
		if (boxes.isEmpty()) throw new IllegalArgumentException("a shape needs at least one box; clear it instead");
	}

	@Override
	public void clearShape(ServerLevel level, BlockPos pos) {
		Map<Long, Map<Long, List<BlockShapesS2C.Box>>> inLevel = shapes.get(level.dimension());
		if (inLevel == null) return;
		long chunk = ChunkPos.pack(pos);
		Map<Long, List<BlockShapesS2C.Box>> inChunk = inLevel.get(chunk);
		if (inChunk == null || inChunk.remove(pos.asLong()) == null) return;
		if (inChunk.isEmpty()) inLevel.remove(chunk, inChunk);
		tell(level, chunk, List.of(new BlockShapesS2C.Entry(pos.asLong(), List.of())));
	}

	/** @hidden */
	public void clear() {
		shapes.clear();
	}

	/**
	 * For a player now in a world, or whose client has just said what it reads. A client without
	 * the chunk channel is sent the whole set for that world, replacing whatever it held, even when
	 * empty. One with it is sent the chunks it already has, since they may have gone before it
	 * could be told, and the rest go as their chunks do.
	 */
	public void sendAll(ServerPlayer player) {
		if (!reads(player)) return;
		ServerLevel level = player.level();
		Map<Long, Map<Long, List<BlockShapesS2C.Box>>> inLevel = shapes.get(level.dimension());
		if (ServerPlayNetworking.canSend(player, BlockShapesV2S2C.TYPE)) {
			if (inLevel == null) return;
			for (long chunk : inLevel.keySet()) {
				if (Watching.has(player, chunk)) sendChunk(player, level, chunk);
			}
			return;
		}
		List<BlockShapesS2C.Entry> entries = new ArrayList<>();
		if (inLevel != null) {
			for (Map<Long, List<BlockShapesS2C.Box>> inChunk : inLevel.values()) entries(inChunk, entries);
		}
		sendWhole(player, level, true, entries);
	}

	/**
	 * As this chunk is sent to the player: every shape in it, if there are any, for a client that
	 * reads them by chunk.
	 *
	 * @hidden
	 */
	public void sendChunk(ServerPlayer player, ServerLevel level, long chunk) {
		Map<Long, Map<Long, List<BlockShapesS2C.Box>>> inLevel = shapes.get(level.dimension());
		Map<Long, List<BlockShapesS2C.Box>> inChunk = inLevel == null ? null : inLevel.get(chunk);
		// The client dropped the chunk's shapes with the chunk, so a chunk with none needs no word.
		if (inChunk == null || inChunk.isEmpty() || !reads(player) || !ServerPlayNetworking.canSend(player, BlockShapesV2S2C.TYPE)) return;
		List<BlockShapesS2C.Entry> entries = new ArrayList<>();
		entries(inChunk, entries);
		sendChunk(player, level, chunk, true, entries);
	}

	private static void entries(Map<Long, List<BlockShapesS2C.Box>> inChunk, List<BlockShapesS2C.Entry> into) {
		inChunk.forEach((pos, boxes) -> into.add(new BlockShapesS2C.Entry(pos, boxes)));
	}

	/**
	 * The capability, not just the channel: it is what the server block asks before giving a
	 * player the shape, and the two must agree or the player rubber-bands.
	 */
	private static boolean reads(ServerPlayer player) {
		return PandoricalApi.hasCapability(player, Capabilities.BLOCK_SHAPES);
	}

	private void tell(ServerLevel level, long chunk, List<BlockShapesS2C.Entry> entries) {
		for (ServerPlayer player : level.players()) {
			if (!reads(player)) continue;
			if (ServerPlayNetworking.canSend(player, BlockShapesV2S2C.TYPE)) {
				if (Watching.has(player, chunk)) sendChunk(player, level, chunk, false, entries);
			} else {
				sendWhole(player, level, false, entries);
			}
		}
	}

	private static void sendChunk(ServerPlayer player, ServerLevel level, long chunk, boolean replace, List<BlockShapesS2C.Entry> entries) {
		// Only the first part replaces; the rest add to it.
		boolean first = true;
		for (List<BlockShapesS2C.Entry> part : parts(entries)) {
			ServerPlayNetworking.send(player, new BlockShapesV2S2C(level.dimension().identifier(), chunk, replace && first, part));
			first = false;
		}
	}

	private static void sendWhole(ServerPlayer player, ServerLevel level, boolean replace, List<BlockShapesS2C.Entry> entries) {
		if (!ServerPlayNetworking.canSend(player, BlockShapesS2C.TYPE)) return;
		// Only the first part replaces; the rest add to it.
		boolean first = true;
		for (List<BlockShapesS2C.Entry> part : parts(entries)) {
			ServerPlayNetworking.send(player, new BlockShapesS2C(level.dimension().identifier(), replace && first, part));
			first = false;
		}
	}

	/** Roughly this many bytes a packet, well under the most a payload may be: a whole world's shapes can be more. */
	private static final int PART_BYTES = 256 * 1024;

	/** The entries in runs that each fit a packet; one empty run for none, so a replace still goes. */
	private static List<List<BlockShapesS2C.Entry>> parts(List<BlockShapesS2C.Entry> entries) {
		List<List<BlockShapesS2C.Entry>> parts = new ArrayList<>();
		int from = 0, bytes = 0;
		for (int i = 0; i < entries.size(); i++) {
			int size = 16 + entries.get(i).boxes().size() * 24;
			if (bytes + size > PART_BYTES && i > from) {
				parts.add(entries.subList(from, i));
				from = i;
				bytes = 0;
			}
			bytes += size;
		}
		parts.add(entries.subList(from, entries.size()));
		return parts;
	}
}
