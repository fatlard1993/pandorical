package justfatlard.pandorical;

import java.util.function.BiPredicate;
import justfatlard.pandorical.api.BlockMarkApi;
import justfatlard.pandorical.api.PandoricalApi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;

/**
 * Whether a block carries a mark, asked from code that runs on either side.
 *
 * <p>Block shape logic runs on the server, where the marks live, and on the client, where the
 * copies it was sent live; the same question has to reach whichever is underneath. The client
 * lends its answer at startup, since this class cannot name client code.
 */
public final class BlockMarkLookup {
	private BlockMarkLookup() {}

	/** The client's answer for its own level: set as the client starts, false until then. */
	public static volatile BiPredicate<BlockPos, String> client = (pos, mark) -> false;

	public static boolean has(LevelReader level, BlockPos pos, String mark) {
		if (level instanceof ServerLevel server) return PandoricalApi.blockMarks().isMarked(server, pos, mark);
		if (level instanceof Level plain && plain.isClientSide()) return client.test(pos, mark);
		return false;
	}

	/** A fence or wall marked so stands alone: a post, joined to nothing. */
	public static boolean isPost(LevelReader level, BlockPos pos) {
		return has(level, pos, BlockMarkApi.POST);
	}
}
