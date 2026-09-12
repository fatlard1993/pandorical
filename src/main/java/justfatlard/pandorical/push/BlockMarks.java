package justfatlard.pandorical.push;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import justfatlard.pandorical.api.BlockMarkApi;
import justfatlard.pandorical.protocol.BlockMarksS2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Marks by level, pushed as they change and whole to whoever arrives. */
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

	/** @hidden A stopped server's marks belong to its world; the next world starts with none. */
	public void clear() {
		marks.clear();
	}

	/** Everything marked in the player's level, for someone who has just arrived in it. */
	public void sendAll(ServerPlayer player) {
		Map<Long, Set<String>> inLevel = marks.get(player.level().dimension());
		if (inLevel == null || inLevel.isEmpty()) return;
		List<BlockMarksS2C.Entry> entries = new ArrayList<>();
		for (var e : inLevel.entrySet()) {
			for (String mark : e.getValue()) entries.add(new BlockMarksS2C.Entry(e.getKey(), mark, true));
		}
		send(player, player.level(), entries);
	}

	private void tell(ServerLevel level, List<BlockMarksS2C.Entry> entries) {
		for (ServerPlayer player : level.players()) send(player, level, entries);
	}

	private void send(ServerPlayer player, ServerLevel level, List<BlockMarksS2C.Entry> entries) {
		if (!ServerPlayNetworking.canSend(player, BlockMarksS2C.TYPE)) return;
		ServerPlayNetworking.send(player, new BlockMarksS2C(level.dimension().identifier(), entries));
	}
}
