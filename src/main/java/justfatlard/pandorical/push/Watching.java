package justfatlard.pandorical.push;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/** Which chunks a player has been sent, asked the way vanilla's own chunk broadcasts ask it. */
final class Watching {
	private Watching() {}

	/**
	 * Sent and not since dropped. A chunk still waiting to go is not counted: what is in it goes
	 * with it when it is sent, so a change made meanwhile is not lost by being left out here.
	 */
	static boolean has(ServerPlayer player, long chunk) {
		return player.getChunkTrackingView().contains(ChunkPos.getX(chunk), ChunkPos.getZ(chunk))
			&& !player.connection.chunkSender.isPending(chunk);
	}
}
