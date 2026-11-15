package justfatlard.pandorical.push;

import justfatlard.pandorical.api.BlockMarkApi;
import justfatlard.pandorical.protocol.BlockMarksS2C;
import justfatlard.pandorical.protocol.BlockMarksV2S2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockMarks implements BlockMarkApi {
	public static final BlockMarks INSTANCE = new BlockMarks();

	private final Map<ResourceKey<Level>, Map<Long, Set<String>>> marks = new ConcurrentHashMap<>();

	private BlockMarks() {}

	@Override
	public void mark(ServerLevel level, BlockPos pos, String mark) {
		Set<String> at = marks.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>())
			.computeIfAbsent(pos.asLong(), k -> ConcurrentHashMap.newKeySet());
		if (!at.add(mark)) return;
		tell(level, List.of(new BlockMarksS2C.Entry(pos.asLong(), mark, true)));
	}

	@Override
	public void unmark(ServerLevel level, BlockPos pos, String mark) {
		Map<Long, Set<String>> inLevel = marks.get(level.dimension());
		if (inLevel == null) return;
		Set<String> at = inLevel.get(pos.asLong());
		if (at == null || !at.remove(mark)) return;
		if (at.isEmpty()) inLevel.remove(pos.asLong());
		tell(level, List.of(new BlockMarksS2C.Entry(pos.asLong(), mark, false)));
	}

	@Override
	public boolean isMarked(ServerLevel level, BlockPos pos, String mark) {
		Map<Long, Set<String>> inLevel = marks.get(level.dimension());
		Set<String> at = inLevel == null ? null : inLevel.get(pos.asLong());
		return at != null && at.contains(mark);
	}

	/** @hidden */
	public void clear() {
		marks.clear();
	}

	/**
	 * Every mark in the world this player is now in. A client with the second channel is told it is
	 * the whole set, even when empty, and drops what it held; one with only the first is sent the
	 * marks on top of whatever it has.
	 */
	public void sendAll(ServerPlayer player) {
		Map<Long, Set<String>> inLevel = marks.get(player.level().dimension());
		List<BlockMarksS2C.Entry> entries = new ArrayList<>();
		if (inLevel != null) {
			for (var e : inLevel.entrySet()) {
				for (String mark : e.getValue()) entries.add(new BlockMarksS2C.Entry(e.getKey(), mark, true));
			}
		}
		send(player, player.level(), true, entries);
	}

	private void tell(ServerLevel level, List<BlockMarksS2C.Entry> entries) {
		for (ServerPlayer player : level.players()) send(player, level, false, entries);
	}

	private void send(ServerPlayer player, ServerLevel level, boolean replace, List<BlockMarksS2C.Entry> entries) {
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
