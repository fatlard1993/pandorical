package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.renderer.ClientBlockMarks;
import justfatlard.pandorical.client.renderer.ClientBlockShapes;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The server's word that a chunk is gone, rather than the chunk cache's: after a long teleport
 * the cache has already moved its centre and passes over the old chunks without unloading them,
 * so no unload event fires for them. A respawn is the same word for every chunk: the server sends
 * the respawned player each chunk again, but forgets none for a player who died, and the world
 * stays the same one when the respawn is in it.
 */
@Mixin(ClientPacketListener.class)
public abstract class ForgetChunkMixin {
	@Shadow private net.minecraft.client.multiplayer.ClientLevel level;

	@Inject(method = "handleForgetLevelChunk", at = @At("TAIL"))
	private void pandorical$forgetChunkState(ClientboundForgetLevelChunkPacket packet, CallbackInfo ci) {
		if (level == null) return;
		var dimension = level.dimension().identifier();
		long chunk = packet.pos().pack();
		ClientBlockMarks.forgetChunk(dimension, chunk);
		ClientBlockShapes.forgetChunk(dimension, chunk);
	}

	@Inject(method = "handleRespawn", at = @At("TAIL"))
	private void pandorical$forgetOnRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
		ClientBlockMarks.forgetWorlds();
		ClientBlockShapes.forgetWorlds();
	}
}
