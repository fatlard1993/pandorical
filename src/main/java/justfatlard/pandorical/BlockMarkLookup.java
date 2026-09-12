package justfatlard.pandorical;

import justfatlard.pandorical.api.BlockMarkApi;
import justfatlard.pandorical.api.PandoricalApi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;

import java.util.function.BiPredicate;

/**
 * Block marks for code that runs on either side. The client sets {@link #client} at startup,
 * since common code cannot name client classes.
 */
public final class BlockMarkLookup {
	private BlockMarkLookup() {}

	public static volatile BiPredicate<BlockPos, String> client = (pos, mark) -> false;

	public static boolean has(LevelReader level, BlockPos pos, String mark) {
		if (level instanceof ServerLevel server) return PandoricalApi.blockMarks().isMarked(server, pos, mark);
		if (level instanceof Level plain && plain.isClientSide()) return client.test(pos, mark);
		return false;
	}

	public static boolean isPost(LevelReader level, BlockPos pos) {
		return has(level, pos, BlockMarkApi.POST);
	}
}
