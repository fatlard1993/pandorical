package justfatlard.pandorical.mixin;

import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.push.BlockShapes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** What Pandorical keeps per position goes to a player with the chunk it stands in, right behind it. */
@Mixin(PlayerChunkSender.class)
public abstract class ChunkSentMixin {
	@Inject(method = "sendChunk", at = @At("TAIL"))
	private static void pandorical$sendChunkState(ServerGamePacketListenerImpl connection, ServerLevel level, LevelChunk chunk, CallbackInfo ci) {
		long at = chunk.getPos().pack();
		PandoricalApi.blockMarksImpl().sendChunk(connection.player, level, at);
		BlockShapes.INSTANCE.sendChunk(connection.player, level, at);
	}
}
