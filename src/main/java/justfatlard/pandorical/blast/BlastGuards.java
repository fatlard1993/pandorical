package justfatlard.pandorical.blast;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.api.BlastApi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** The guards mods registered, asked as each explosion is about to break what it found. See {@link BlastApi}. */
public final class BlastGuards implements BlastApi {
	public static final BlastGuards INSTANCE = new BlastGuards();

	private BlastGuards() {}

	private static final List<Guard> GUARDS = new CopyOnWriteArrayList<>();

	@Override
	public void protect(Guard guard) {
		GUARDS.add(guard);
	}

	/** The positions with every spared one taken out; the same list when none is, which is nearly always. */
	public static List<BlockPos> withoutSpared(ServerLevel level, List<BlockPos> positions) {
		if (GUARDS.isEmpty()) return positions;
		List<BlockPos> kept = null;
		for (int i = 0; i < positions.size(); i++) {
			BlockPos pos = positions.get(i);
			if (spared(level, pos)) {
				// Copied once something needs sparing, and mutable: vanilla shuffles the list in place
				if (kept == null) kept = new ArrayList<>(positions.subList(0, i));
			} else if (kept != null) {
				kept.add(pos);
			}
		}
		return kept == null ? positions : kept;
	}

	private static boolean spared(ServerLevel level, BlockPos pos) {
		for (Guard guard : GUARDS) {
			try {
				if (guard.spares(level, pos)) return true;
			} catch (RuntimeException e) {
				Pandorical.LOGGER.error("A blast guard threw; the block takes its chances", e);
			}
		}
		return false;
	}
}
