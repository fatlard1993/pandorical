package justfatlard.pandorical.push;

import justfatlard.pandorical.api.BlockMarkApi;
import justfatlard.pandorical.protocol.BlockMarksS2C;
import justfatlard.pandorical.protocol.BlockMarksV2S2C;
import justfatlard.pandorical.protocol.BlockMarksV3S2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Marks by position, per world and chunk. A client with the chunk channel is told a chunk's marks
 * as the chunk is sent to it, and changes only while it has the chunk; one without is told the
 * whole world as it arrives in it, and every change in that world.
 */
public final class BlockMarks implements BlockMarkApi {
	public static final BlockMarks INSTANCE = new BlockMarks();

	/** World, then packed chunk, then packed position. */
	private final Map<ResourceKey<Level>, Map<Long, Map<Long, Set<String>>>> marks = new ConcurrentHashMap<>();

	private BlockMarks() {}

	@Override
	public void mark(ServerLevel level, BlockPos pos, String mark) {
		long chunk = ChunkPos.pack(pos);
		Set<String> at = marks.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>())
			.computeIfAbsent(chunk, k -> new ConcurrentHashMap<>())
			.computeIfAbsent(pos.asLong(), k -> ConcurrentHashMap.newKeySet());
		if (!at.add(mark)) return;
		tell(level, chunk, List.of(new BlockMarksS2C.Entry(pos.asLong(), mark, true)));
	}

	@Override
	public void unmark(ServerLevel level, BlockPos pos, String mark) {
		Map<Long, Map<Long, Set<String>>> inLevel = marks.get(level.dimension());
		if (inLevel == null) return;
		long chunk = ChunkPos.pack(pos);
		Map<Long, Set<String>> inChunk = inLevel.get(chunk);
		Set<String> at = inChunk == null ? null : inChunk.get(pos.asLong());
		if (at == null || !at.remove(mark)) return;
		if (at.isEmpty()) inChunk.remove(pos.asLong(), at);
		if (inChunk.isEmpty()) inLevel.remove(chunk, inChunk);
		tell(level, chunk, List.of(new BlockMarksS2C.Entry(pos.asLong(), mark, false)));
	}

	@Override
	public boolean isMarked(ServerLevel level, BlockPos pos, String mark) {
		Map<Long, Map<Long, Set<String>>> inLevel = marks.get(level.dimension());
		Map<Long, Set<String>> inChunk = inLevel == null ? null : inLevel.get(ChunkPos.pack(pos));
		Set<String> at = inChunk == null ? null : inChunk.get(pos.asLong());
		return at != null && at.contains(mark);
	}

	/** @hidden */
	public void clear() {
		marks.clear();
	}

	/**
	 * For a player now in a world, or whose client has just said what it reads. A client with only
	 * the older channels is sent every mark in the world: with the second it is told that is the
	 * whole set, even when empty, and drops what it held; with only the first it is sent the marks
	 * on top of whatever it has. A client with the chunk channel is sent the chunks it already has,
	 * since they may have gone before it could be told, and the rest go as their chunks do.
	 */
	public void sendAll(ServerPlayer player) {
		ServerLevel level = player.level();
		Map<Long, Map<Long, Set<String>>> inLevel = marks.get(level.dimension());
		if (ServerPlayNetworking.canSend(player, BlockMarksV3S2C.TYPE)) {
			if (inLevel == null) return;
			for (long chunk : inLevel.keySet()) {
				if (Watching.has(player, chunk)) sendChunk(player, level, chunk);
			}
			return;
		}
		List<BlockMarksS2C.Entry> entries = new ArrayList<>();
		if (inLevel != null) {
			for (Map<Long, Set<String>> inChunk : inLevel.values()) entries(inChunk, entries);
		}
		sendWhole(player, level, true, entries);
	}

	/**
	 * As this chunk is sent to the player: every mark in it, if there are any, for a client that
	 * reads them by chunk.
	 *
	 * @hidden
	 */
	public void sendChunk(ServerPlayer player, ServerLevel level, long chunk) {
		Map<Long, Map<Long, Set<String>>> inLevel = marks.get(level.dimension());
		Map<Long, Set<String>> inChunk = inLevel == null ? null : inLevel.get(chunk);
		// The client dropped the chunk's marks with the chunk, so a chunk with none needs no word.
		if (inChunk == null || inChunk.isEmpty() || !ServerPlayNetworking.canSend(player, BlockMarksV3S2C.TYPE)) return;
		List<BlockMarksS2C.Entry> entries = new ArrayList<>();
		entries(inChunk, entries);
		sendChunk(player, level, chunk, true, entries);
	}

	private static void entries(Map<Long, Set<String>> inChunk, List<BlockMarksS2C.Entry> into) {
		for (var e : inChunk.entrySet()) {
			for (String mark : e.getValue()) into.add(new BlockMarksS2C.Entry(e.getKey(), mark, true));
		}
	}

	private void tell(ServerLevel level, long chunk, List<BlockMarksS2C.Entry> entries) {
		for (ServerPlayer player : level.players()) {
			if (ServerPlayNetworking.canSend(player, BlockMarksV3S2C.TYPE)) {
				if (Watching.has(player, chunk)) sendChunk(player, level, chunk, false, entries);
			} else {
				sendWhole(player, level, false, entries);
			}
		}
	}

	private static void sendChunk(ServerPlayer player, ServerLevel level, long chunk, boolean replace, List<BlockMarksS2C.Entry> entries) {
		// Only the first part replaces; the rest add to it.
		boolean first = true;
		for (List<BlockMarksS2C.Entry> part : parts(entries)) {
			ServerPlayNetworking.send(player, new BlockMarksV3S2C(level.dimension().identifier(), chunk, replace && first, part));
			first = false;
		}
	}

	private static void sendWhole(ServerPlayer player, ServerLevel level, boolean replace, List<BlockMarksS2C.Entry> entries) {
		if (ServerPlayNetworking.canSend(player, BlockMarksV2S2C.TYPE)) {
			// Only the first part replaces; the rest add to it.
			boolean first = true;
			for (List<BlockMarksS2C.Entry> part : parts(entries)) {
				ServerPlayNetworking.send(player, new BlockMarksV2S2C(level.dimension().identifier(), replace && first, part));
				first = false;
			}
		} else if (ServerPlayNetworking.canSend(player, BlockMarksS2C.TYPE) && !entries.isEmpty()) {
			for (List<BlockMarksS2C.Entry> part : parts(entries)) {
				ServerPlayNetworking.send(player, new BlockMarksS2C(level.dimension().identifier(), part));
			}
		}
	}

	/** Roughly this many bytes a packet, well under the most a payload may be: a whole world's marks can be more. */
	private static final int PART_BYTES = 256 * 1024;

	/** The entries in runs that each fit a packet; one empty run for none, so a replace still goes. */
	private static List<List<BlockMarksS2C.Entry>> parts(List<BlockMarksS2C.Entry> entries) {
		List<List<BlockMarksS2C.Entry>> parts = new ArrayList<>();
		int from = 0, bytes = 0;
		for (int i = 0; i < entries.size(); i++) {
			int size = 16 + entries.get(i).mark().length() * 3;
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
